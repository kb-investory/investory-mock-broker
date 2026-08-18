# Investory Mock Broker API 명세

베이스 URL(로컬 기준): `http://localhost:8080`

## 공통 사항

### 인증

| 구분 | 헤더 | 비고 |
|---|---|---|
| `/mock/auth/**` | 없음 | 로그인/회원가입 자체이므로 토큰이 없다 |
| `/mock/system/**` | `x-client-id` + `x-client-secret` | 커넥션 발급 자체이므로 아직 connectionId가 없다 |
| `/mock/**` (그 외) | `Authorization: Bearer {accessToken}` **또는** `x-client-id`+`x-client-secret`+`x-connection-id` | 어느 쪽이든 결국 같은 유저 데이터만 조회·조작하도록 스코프된다 |
| `/v2/invest/**` | 위 두 방식 중 하나 + `x-api-tran-id: {거래고유번호}` | 마이데이터 규격 공통 헤더 |

인증 경로는 두 가지다.

1. **accessToken (유저 본인)** — `/mock/auth/login` 또는 `/mock/auth/register` 응답으로 받는다. 토큰은
   유저 한 명당 하나씩 안정적으로 유지되며(재로그인해도 데이터가 재시드되지 않고 토큰도 재사용됨),
   여러 유저가 동시에 로그인해 있어도 서로의 데이터에 전혀 영향을 주지 않는다.
2. **client_id/secret + connectionId (외부 서비스)** — `/mock/system/connections`(뒤에서 설명)로 발급받은
   connectionId를 계속 재사용하는 경로. accessToken 발급/재발급을 신경 쓸 필요가 없다. 자세한 내용은
   [외부 서비스 연동](#외부-서비스-연동-mocksystem) 참고.

> `/mock/state`·`/mock/auth/login` 응답의 `connectionId` 필드는 위 2번과 이름만 같을 뿐 다른 값이다 —
> 유저 한 명당 1개씩 고정으로 붙어있는 필드이고, 마이데이터 흉내를 위한 것이지 이 문서의 커넥션
> 인증과는 무관하다.

### 오류 응답 형식

모든 오류는 아래 형태로 통일된다.

```json
{ "rsp_code": "40001", "rsp_msg": "설명 메시지" }
```

| rsp_code | HTTP | 상황 |
|---|---|---|
| `40001` | 400 | 필수 파라미터 누락, 형식 오류, 중복 아이디 등 잘못된 요청 |
| `40101` | 401 | 접근토큰 없음/무효, 로그인 실패, systemKey 불일치, client 인증 실패, 커넥션 무효 |
| `40401` | 404 | 계좌·종목·연결을 찾을 수 없음 |
| `50000` | 500 | 처리하지 못한 서버 내부 오류 |
| `50020` | 422 | 예수금 부족 |
| `50021` | 422 | 보유수량 부족 |
| `50022` | 422 | 가격제한폭(상한가/하한가) 초과 |

---

## 인증

### `POST /mock/auth/login` — 로그인

인증 불필요.

**Request**
```json
{ "loginId": "demo1", "loginPassword": "1234" }
```

**Response** `200`
```json
{
  "connectionId": "b3f5c2a1...",
  "accessToken": "9f2e7d0b...",
  "tokenType": "Bearer",
  "orgCode": "S9990001A",
  "orgName": "미래에셋증권(모의)",
  "accounts": [
    { "accountNum": "5011234567", "accountName": "종합위탁계좌", "accountType": "101",
      "issueDate": "20240103", "cashBalance": 6017655.000 }
  ]
}
```

같은 유저로 다시 로그인해도 데이터는 재시드되지 않고 기존 토큰을 그대로 재사용한다.
아이디/비밀번호가 틀리면 `40101` (계정 존재 여부는 구분해서 알려주지 않음).

### `POST /mock/auth/register` — 회원가입

인증 불필요. `systemKey`가 일치해야만 가입된다(`.env`의 `MOCKBROKER_SIGNUP_KEY`로 정함 —
실제 보안장치가 아니라 무분별한 계정 생성 방지용).

**Request**
```json
{ "loginId": "myaccount", "loginPassword": "mypw1234", "systemKey": "<MOCKBROKER_SIGNUP_KEY 값>" }
```

**Response** `200` — `/mock/auth/login`과 동일한 형태로 즉시 로그인 상태의 토큰을 내려준다.
계좌 1개(`9000000001`, 예수금 1,000만원)가 자동 생성된다.

이미 존재하는 `loginId`면 `40001`, `systemKey`가 틀리면 `40101`.

---

## 외부 서비스 연동 (`/mock/system/**`)

목업 서버에 직접 로그인하는 유저 본인이 아니라, **외부 서비스**(예: Investory 백엔드)가
client_id/client_secret으로 자신을 증명하고, 유저의 loginId/loginPassword로 그 유저와의
"커넥션"을 맺어 이후 요청에서 accessToken 없이 그 유저 데이터에 접근하는 두 번째 인증 경로.
실제 마이데이터의 (정보수신기관 인증) + (개별인증으로 얻는 연결정보) 두 단계를 흉내낸다.

기본 클라이언트는 서버 기동 시 자동 등록된다. client_id/client_secret 값은 `.env`의
`MOCKBROKER_CLIENT_ID`/`MOCKBROKER_CLIENT_SECRET`로 정한다.

### `POST /mock/system/connections` — 커넥션 발급

인증: `x-client-id`, `x-client-secret` 헤더.

**Request**
```json
{ "loginId": "demo1", "loginPassword": "1234" }
```

**Response** `200`
```json
{
  "connectionId": "9223c99e18e24f4fa1b8b854f812dfbd",
  "orgCode": "S9990001A",
  "orgName": "미래에셋증권(모의)",
  "accounts": [
    { "accountNum": "5011234567", "accountName": "종합위탁계좌", "accountType": "101",
      "issueDate": "20240103", "cashBalance": 6017655.000 }
  ]
}
```

같은 client가 같은 유저에 대해 다시 요청해도 기존 커넥션을 그대로 재사용한다(재발급이 아니라
같은 connectionId를 돌려준다). client_id/client_secret이 틀렸거나 loginId/loginPassword가
일치하지 않으면 `40101`.

### `GET /mock/system/orgs` — 증권사(org) 전체 목록

인증: `x-client-id`, `x-client-secret` 헤더. 목업 서버가 알고 있는 증권사 목록 전체를
돌려준다 — 외부 서비스가 자기 쪽 증권사 목록과 동기화할 때 쓴다. 시나리오를 적용하거나
회원가입할 때 등장한 orgCode는 자동으로 이 목록에 등록된다(아래 `/mock/admin/scenarios` 참고).

**Response** `200`
```json
[
  { "orgCode": "S9990001A", "orgName": "미래에셋증권(모의)" },
  { "orgCode": "S9990002A", "orgName": "키움증권(모의)" },
  { "orgCode": "S9990099A", "orgName": "테스트증권(모의)" }
]
```

### 커넥션으로 데이터 접근하기

발급받은 `connectionId`로는 `/mock/**`(`/mock/auth/**`·`/mock/system/**` 제외)와
`/v2/invest/**`의 **모든 엔드포인트**를 그대로 쓸 수 있다 — 별도 엔드포인트가 있는 게 아니라,
아래 세 헤더를 `Authorization: Bearer` 대신 실어 보내면 된다.

```
x-client-id: investory-backend
x-client-secret: {client secret}
x-connection-id: {발급받은 connectionId}
```

예: `GET /mock/state`, `POST /mock/orders`, `GET /v2/invest/accounts?org_code=...` 등 이 문서의
나머지 모든 API가 대상이다. connectionId는 발급받은 client에 스코프되어 있어, 다른 client가
자신의 client_id/secret과 함께 남의 connectionId를 들이밀어도 `40101`이 난다.

---

## 관제 콘솔 API (`/mock/admin/**`)

client_id/secret이나 accessToken과는 별개의 세 번째 인증 경로. 특정 유저·client에 스코프되지
않고 **전체 커넥션 발급 현황을 보고, 유저를 생성·삭제하고, 유저에게 거래 패턴(시나리오)을
적용·관리**하는 운영자용이다.
`/connections.html` 페이지가 이 API를 그대로 쓴다. 계정은 `.env`의
`MOCKBROKER_ADMIN_USER`/`MOCKBROKER_ADMIN_PASSWORD`로 정한다 — 실제 보안장치가 아니라 아무나
이 화면을 보지 못하게 막는 최소한의 문이다.

### `POST /mock/admin/login` — 관리자 로그인

**Request**
```json
{ "username": "admin", "password": "<MOCKBROKER_ADMIN_PASSWORD 값>" }
```
**Response** `200`
```json
{ "adminToken": "fcbaa10880874454aaa94fe99ea3c7d9" }
```
아이디/비밀번호가 틀리면 `40101`.

### `GET /mock/admin/connections` — 전체 커넥션 목록

인증: `x-admin-token` 헤더 (로그인 응답의 `adminToken`). client_id 무관하게 지금까지 발급된
모든 커넥션을 최신순으로 돌려준다.

**Response** `200`
```json
[
  {
    "connectionId": "9223c99e18e24f4fa1b8b854f812dfbd",
    "clientId": "investory",
    "loginId": "demo1",
    "orgName": "미래에셋증권(모의)",
    "createdAt": "2026-08-12 07:35:27"
  }
]
```
토큰이 없거나 유효하지 않으면 `40101`.

### `GET /mock/admin/templates` — 시나리오 템플릿 목록

인증: `x-admin-token` 헤더. `resources/scenarios/*.json`에서 로드된 번들 템플릿과, 관제
콘솔에서 저장한 DB 커스텀 템플릿(`mock_scenario_template`)을 합쳐서 돌려준다. `source`
필드로 출처를 구분한다 (`FILE`은 삭제 불가, `DB`는 삭제 가능).

**Response** `200`
```json
[
  { "templateId": "GROWTH_FOCUS_01", "profileName": "성장주 집중형", "description": "...", "source": "FILE" },
  { "templateId": "MOMENTUM_CHASER_01", "profileName": "추격매수형", "description": "...", "source": "FILE" },
  { "templateId": "AVERAGING_DOWN_01", "profileName": "물타기 반복형", "description": "...", "source": "FILE" },
  { "templateId": "MY_CUSTOM_01", "profileName": "커스텀", "description": "...", "source": "DB" }
]
```

### `POST /mock/admin/templates` — 커스텀 템플릿 저장

인증: `x-admin-token` 헤더. 시나리오 JSON(`templateId`/`profileName`/`description`/`orgCode`/
`orgName`/`prices`/`accounts`/`trades` — [시나리오 적용](#post-mockadminscenarios--유저에게-시나리오-적용)의
`scenario` 필드와 동일한 형태)을 재사용 가능한 템플릿으로 DB에 저장한다(upsert — 같은
`templateId`로 다시 저장하면 갱신). 번들 파일 템플릿과 같은 `templateId`는 거부된다.

**Request**
```json
{
  "templateId": "MY_CUSTOM_01",
  "profileName": "커스텀",
  "description": "직접 입력",
  "orgCode": "S9990001A",
  "orgName": "미래에셋증권(모의)",
  "prices": [ { "prodCode": "005930", "prodName": "삼성전자", "marketType": "KOSPI", "currentPrice": 84200 } ],
  "accounts": [ { "accountNum": "5011234567", "accountName": "종합위탁계좌", "accountType": "101", "issueDate": "20240103", "initialCash": 15000000 } ],
  "trades": [ { "accountNum": "5011234567", "tradedAt": "20260112101204", "side": "BUY", "prodCode": "005930", "quantity": 30, "price": 74800 } ]
}
```

**Response** `200`
```json
{ "templateId": "MY_CUSTOM_01", "profileName": "커스텀", "message": "템플릿을 저장했습니다." }
```

`templateId`가 없으면 `40001`, 번들 파일 템플릿과 겹치면 `40001`.

### `POST /mock/admin/templates/generate` — 과거 시세 기반 템플릿 생성

인증: `x-admin-token` 헤더. 종목별 실제 과거 종가(네이버 일별시세)로 매수 거래를 채운 시나리오를
만들어 커스텀 템플릿으로 저장한다(위 `POST /mock/admin/templates`와 마찬가지로 저장만 하고
유저에게 적용하지는 않는다). 본 서비스 분석 기능이 요구하는 최소 기간치 거래이력을 손으로
가격을 지어내지 않고 준비할 때 쓴다.

`prodCodes`를 비우면 호출 시점 시가총액 순위(코스피 상위 20 + 코스닥 상위 10, ETF·우선주는
최선 노력으로 제외)를 실시간 조회해 기본 바스켓으로 쓴다 — 순위가 바뀌어도 재배포 없이 항상
최신 구성으로 생성된다. 종목당 거래는 `tradesPerProduct`(기본 4)회, `days`(기본 90)일 구간에
고르게 분산되고, 예수금은 종목·거래 수만큼 균등 배분한 뒤 10% 여유를 둬서 재생 중 예수금 부족이
나지 않게 한다. 계좌 개설일(`issueDate`)도 그 기간 시작일로 맞춘다.

**Request**
```json
{
  "templateId": "HISTORICAL_90D_01",
  "profileName": "실거래 90일 이력",
  "description": "선택, 비우면 자동 생성",
  "orgCode": "S9990001A",
  "orgName": "미래에셋증권(모의)",
  "accountNum": "5019999999",
  "accountName": "선택, 기본 종합위탁계좌",
  "accountType": "선택, 기본 101",
  "initialCash": 500000000,
  "days": 90,
  "tradesPerProduct": 4,
  "prodCodes": ["005930", "000660"]
}
```

**Response** `200`
```json
{ "templateId": "HISTORICAL_90D_01", "profileName": "실거래 90일 이력", "tradeCount": 120,
  "message": "과거 시세 기반 템플릿을 생성했습니다." }
```

`templateId`/`orgCode`/`orgName`/`accountNum`이 없으면 `40001`. `prodCodes`를 넘겼는데 그 종목의
과거 시세를 못 가져오면(존재하지 않는 코드 등) `40001`. `prodCodes`를 안 넘겼는데 시가총액 순위
조회 자체가 실패하면(네트워크 문제, `MOCKBROKER_QUOTE_ENABLED=false` 등) `40001` — 이 경우
`prodCodes`를 직접 지정해야 한다.

### `DELETE /mock/admin/templates/{templateId}` — 커스텀 템플릿 삭제

인증: `x-admin-token` 헤더. DB에 저장된 커스텀 템플릿만 삭제 가능하다.

**Response** `200`
```json
{ "templateId": "MY_CUSTOM_01", "message": "템플릿을 삭제했습니다." }
```

번들 파일 템플릿 ID면 `40001`, 존재하지 않는 templateId면 `40401`.

### `GET /mock/admin/orgs` — 증권사(org) 전체 목록

인증: `x-admin-token` 헤더. `mock_org` 테이블에 등록된 증권사 전체를 돌려준다. `/mock/system/orgs`와
같은 데이터를 보여주되, 콘솔(운영자)용 인증 경로라는 점만 다르다.

**Response** `200`
```json
[
  { "orgCode": "S9990001A", "orgName": "미래에셋증권(모의)" },
  { "orgCode": "S9990002A", "orgName": "키움증권(모의)" },
  { "orgCode": "S9990099A", "orgName": "테스트증권(모의)" }
]
```

### `POST /mock/admin/orgs` — 증권사 등록

인증: `x-admin-token` 헤더. 새 증권사를 배포 없이 바로 등록한다. 시나리오를 적용하거나
회원가입할 때 등장하는 orgCode는 여기 등록돼 있지 않아도 자동으로 등록되므로, 이 API는
백엔드와의 목록 동기화를 미리 맞춰두고 싶을 때 쓰면 된다.

**Request**
```json
{ "orgCode": "S9990003A", "orgName": "삼성증권(모의)" }
```

**Response** `200`
```json
{ "orgCode": "S9990003A", "orgName": "삼성증권(모의)" }
```
이미 등록된 orgCode면 `40001`.

### `POST /mock/admin/scenarios` — 유저에게 시나리오 적용

인증: `x-admin-token` 헤더. **이미 존재하는 유저**(`/mock/auth/register`로 미리 만들어둔
계정)에게 템플릿 또는 직접 입력한 JSON을 적용해 계좌·시세·거래이력을 통째로 (재)생성한다.
기존 데이터는 전부 지워진다. `templateId`와 `scenario`(직접 JSON) 중 하나만 채우면 되고,
둘 다 있으면 `templateId`가 우선한다. 여기서 쓰인 orgCode가 `mock_org`에 아직 없으면 자동으로
등록된다.

**Request (템플릿 적용)**
```json
{ "loginId": "demo1", "templateId": "GROWTH_FOCUS_01" }
```

**Request (직접 JSON 적용)**
```json
{
  "loginId": "demo1",
  "scenario": {
    "profileName": "커스텀",
    "description": "직접 입력",
    "orgCode": "S9990001A",
    "orgName": "미래에셋증권(모의)",
    "prices": [ { "prodCode": "005930", "prodName": "삼성전자", "marketType": "KOSPI", "currentPrice": 84200 } ],
    "accounts": [ { "accountNum": "5011234567", "accountName": "종합위탁계좌", "accountType": "101", "issueDate": "20240103", "initialCash": 15000000 } ],
    "trades": [ { "accountNum": "5011234567", "tradedAt": "20260112101204", "side": "BUY", "prodCode": "005930", "quantity": 30, "price": 74800 } ]
  }
}
```

**Response** `200`
```json
{ "loginId": "demo1", "orgCode": "S9990001A", "orgName": "미래에셋증권(모의)" }
```

등록되지 않은 `loginId`거나 존재하지 않는 `templateId`면 `40401`, `loginId`가 없거나
`templateId`/`scenario`가 둘 다 없으면 `40001`.

### `POST /mock/admin/users` — 유저 생성 (+ 즉시 템플릿 적용)

인증: `x-admin-token` 헤더. 유저부터 만들고(기본 org/계좌), `templateId`나 `scenario`가 있으면
이어서 바로 적용까지 한 번에 한다. `/mock/auth/register`와 달리 `systemKey`는 필요 없다
(이미 admin 토큰으로 인증됨).

**Request (빈 계정만 생성)**
```json
{ "loginId": "demo2", "loginPassword": "1234" }
```

**Request (생성과 동시에 템플릿 적용)**
```json
{ "loginId": "demo2", "loginPassword": "1234", "templateId": "GROWTH_FOCUS_01" }
```

**Response** `200`
```json
{ "loginId": "demo2", "orgCode": "S9990001A", "orgName": "미래에셋증권(모의)" }
```

`loginId`/`loginPassword`가 없으면 `40001`, 이미 사용 중인 `loginId`면 `40001`.

### `GET /mock/admin/users` — 전체 유저 목록

인증: `x-admin-token` 헤더.

**Response** `200`
```json
[
  { "loginId": "demo1", "orgName": "미래에셋증권(모의)", "scenarioApplied": true },
  { "loginId": "demo2", "orgName": "테스트증권(모의)", "scenarioApplied": false }
]
```

### `DELETE /mock/admin/users/{loginId}` — 유저 삭제

인증: `x-admin-token` 헤더. 유저와 그 유저의 계좌·보유종목·거래내역·시세·커넥션을 전부
연쇄 삭제한다. 살아있는 `accessToken`이 있으면 같이 무효화된다(이후 그 토큰으로 오는 요청은
`40101`).

**Response** `200`
```json
{ "loginId": "demo2", "message": "유저를 삭제했습니다." }
```

등록되지 않은 `loginId`면 `40401`.

---

## 테스트 편의 API (`/mock/test/**`, 인증 불필요)

메인 서버 연동 테스트 전용 지름길이다. accessToken 발급(로그인) 없이 `loginId`만으로
"유저가 방금 증권사 앱에서 거래를 체결했다"를 즉시 만들어낸다. 이어서 메인 서버의 거래내역
동기화 API를 호출하면, 로그인 단계 없이 한 번의 스크립트로 "거래 발생 → 메인 서버 반영" 전체
흐름을 재현할 수 있다. 실제 계정 자산과 무관한 목업 데이터에만 작용하므로 인증을 두지 않았다.

### `POST /mock/test/trade` — loginId 기반 즉시 체결

**Request**
```json
{
  "loginId": "demo1",
  "accountNum": null,
  "prodCode": "005930",
  "side": "BUY",
  "quantity": 10,
  "price": null,
  "tradedAt": null
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `loginId` | Y | 데모 유저의 로그인 아이디 |
| `accountNum` | N | 생략하면 이 유저의 첫 번째 계좌를 쓴다 |
| `prodCode` | Y | 종목코드 |
| `side` | Y | `BUY` 또는 `SELL` |
| `quantity` | Y | 수량 |
| `price` | N | 생략하면 현재가로 체결 |
| `tradedAt` | N | `yyyyMMddHHmmss`. 채우면 과거 거래 소급 등록 |

**Response** `200` — `/mock/orders`와 동일한 형태([아래](#post-mockorders--매수매도-주문) 참고).

등록되지 않은 `loginId`면 `40401`.

---

## 데모 조작 API (`/mock/**`, `Authorization` 필요)

### `POST /mock/orders` — 매수·매도 주문

**Request**
```json
{
  "accountNum": "5011234567",
  "prodCode": "005930",
  "side": "BUY",
  "quantity": 10,
  "price": 84200,
  "tradedAt": null
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `accountNum` | Y | 계좌번호 |
| `prodCode` | Y | 종목코드. 처음 보는 종목이면 그 자리에서 코스피 마스터 목록을 조회해 활성화한다 |
| `side` | Y | `BUY` 또는 `SELL` |
| `quantity` | Y | 수량 |
| `price` | N | 생략하면 현재가로 체결 |
| `tradedAt` | N | `yyyyMMddHHmmss`. 채우면 과거 거래 소급 등록(가격제한폭 검증 안 받고, 종목 현재가도 안 바뀜). 생략하면 지금 시각의 라이브 주문 |

**Response** `200`
```json
{
  "accountNum": "5011234567", "transNo": "20260730-0001", "transDtime": "20260730104159",
  "side": "BUY", "prodCode": "005930", "prodName": "삼성전자",
  "quantity": 10, "unitPrice": 84200, "transAmt": 842000.000,
  "commission": 126, "tax": 0, "settleAmt": 842126.000,
  "cashBalance": 9157874.000, "holdingNum": 10, "avgPrice": 84200
}
```

라이브 주문에는 현재가 대비 ±30% 가격제한폭이 적용된다(`50022`). 예수금/보유수량 부족 시
각각 `50020`/`50021`.

### `POST /mock/reset` — 초기화

요청 바디 없음. 로그인된 유저의 데이터만, **관제 콘솔에서 마지막으로 적용된 시나리오** 기준으로
되돌린다(다른 유저는 영향 없음).

**Response** `200`
```json
{ "orgName": "미래에셋증권(모의)", "message": "초기 상태로 되돌렸습니다." }
```

아직 시나리오가 한 번도 적용되지 않은 유저(예: 방금 회원가입만 하고 관제 콘솔에서 시나리오를
적용받지 않은 경우)면 `40001`.

### `GET /mock/state?accountNum={선택}` — 콘솔용 통합 상태 조회

`accountNum` 생략 시 첫 번째 계좌 기준.

**Response** `200`
```json
{
  "connectionId": "b3f5c2a1...", "orgCode": "S9990001A", "orgName": "미래에셋증권(모의)",
  "selectedAccountNum": "5011234567", "cashBalance": 6017655, "totalEvalAmt": 29173750,
  "accounts": [ { "accountNum": "...", "accountName": "...", "cashBalance": 0 } ],
  "holdings": [ { "prodCode": "...", "prodName": "...", "holdingNum": "10", "avgPrice": 84200,
                  "currentPrice": 84200, "purchaseAmt": 842000, "evalAmt": 842000, "pnl": 0 } ],
  "transactions": [ { "transNo": "...", "transDtime": "...", "transType": "101",
                      "transTypeDetail": "주식 매수", "prodName": "...", "transNum": "10",
                      "baseAmt": 84200, "settleAmt": 842126 } ],
  "prices": [ { "prodCode": "...", "prodName": "...", "currentPrice": 84200 } ]
}
```

`prices`는 이 유저 앞으로 **이미 활성화된(조회되었거나 거래된)** 종목만 담는다 — 코스피 전 종목이
아니다.

### `GET /mock/products` — 코스피 전 종목 코드·이름 목록

네트워크 호출 없이 즉시 응답(콘솔 종목 선택창용).

**Response** `200`
```json
{ "products": [ { "prodCode": "005930", "prodName": "삼성전자" }, ... ] }
```
(약 944건, 우선주 포함)

### `GET /mock/prices/{prodCode}` — 종목 현재가 조회

이미 활성화된 종목이면 저장된 값을, 처음 보는 종목이면 그 순간 네이버에서 실시간으로 가져와
활성화한 뒤 돌려준다.

**Response** `200`
```json
{ "prodCode": "005930", "prodName": "삼성전자", "currentPrice": 84200 }
```
마스터 목록에도 없는 코드면 `40401`.

### `POST /mock/prices` — 현재가 수동 조정

**Request**
```json
{ "prodCode": "005930", "currentPrice": 90000 }
```
**Response** `200`
```json
{ "prodCode": "005930", "prodName": "삼성전자", "currentPrice": 90000 }
```

### `POST /mock/prices/refresh` — 보유 종목 시세 재동기화

요청 바디 없음. **보유 중인 종목만** 네이버 실시간 시세로 다시 맞춘다.

**Response** `200`
```json
{
  "updated": [ { "prodCode": "005930", "prodName": "삼성전자", "currentPrice": 84500 } ],
  "updatedCount": 1, "failedCount": 0
}
```

---

## 마이데이터 규격 API (`/v2/invest/**`)

공통 요청 헤더: `Authorization: Bearer {accessToken}`, `x-api-tran-id: {거래고유번호}`
(응답 헤더로 그대로 반환됨), `x-api-type` (규격상 필요하나 검증 안 함).

모든 성공 응답은 `rsp_code: "00000"`, `rsp_msg`를 공통으로 포함한다 (아래 예시에서 생략).

### `GET /v2/invest/accounts?org_code={코드}&limit={선택,기본500}` — 금투-001 계좌 목록 조회

```json
{
  "search_timestamp": "20260730104159", "account_cnt": 1,
  "account_list": [
    { "account_num": "5011234567", "is_consent": true, "account_name": "종합위탁계좌",
      "account_type": "101", "issue_date": "20240103", "is_tax_benefits": false,
      "is_cma": false, "is_stock_trans": false, "is_account_link": false }
  ]
}
```

### `POST /v2/invest/accounts/basic` — 금투-002 계좌 기본정보 조회

**Request** `{ "account_num": "5011234567" }`
```json
{
  "search_timestamp": "...", "base_date": "20260730", "basic_cnt": 1,
  "basic_list": [ { "currency_code": "KRW", "withholdings_amt": 6017655,
                    "credit_loan_amt": 0, "mortgage_amt": 0, "avail_balance": 6017655 } ]
}
```

### `POST /v2/invest/accounts/transactions` — 금투-003 거래내역 조회 (거래일시 내림차순)

**Request**
```json
{ "account_num": "5011234567", "from_date": "20260101", "to_date": "20261231",
  "limit": 100, "next_page": null }
```
```json
{
  "trans_cnt": 1, "next_page": "1",
  "trans_list": [
    { "prod_name": "삼성전자", "prod_code": "005930", "trans_dtime": "20260112101204",
      "trans_no": "20260112-0001", "trans_type": "101", "trans_type_detail": "주식 매수",
      "trans_num": 30, "trans_unit": "주", "base_amt": 74800, "trans_amt": 2244000,
      "settle_amt": 2244337, "balance_amt": 12755663, "currency_code": "KRW", "ex_code": "FRK" }
  ]
}
```
`next_page`는 더 가져올 페이지가 있을 때만 포함된다.

### `POST /v2/invest/accounts/products` — 금투-004 보유 상품정보 조회 (종목코드 오름차순)

**Request** `{ "account_num": "5011234567" }`
```json
{
  "base_date": "20260730", "prod_cnt": 1,
  "prod_list": [
    { "prod_type": "401", "prod_type_detail": "주식", "prod_code": "005930", "ex_code": "FRK",
      "prod_name": "삼성전자", "credit_type": "01", "is_tax_benefits": false,
      "purchase_amt": 4210000, "holding_num": 50, "trans_unit": "주",
      "eval_amt": 4210000, "is_execution": true, "currency_code": "KRW" }
  ]
}
```
