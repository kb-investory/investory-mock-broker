# Investory Mock Broker

마이데이터 **금융투자 업권 정보제공 API 규격**(FSAG0402)을 모방한 가상 증권사 서버입니다.
Investory 본 서비스가 실제 증권사 대신 이 서버에 붙어 계좌·거래내역·보유종목을 조회하고,
직접 매수·매도 주문을 넣어 신규 거래가 반영되는 모습을 확인할 수 있게 하는 것이 목적입니다.

> 이 서버는 **Investory 본 서비스가 아니라 외부 기관(증권사) 역할을 하는 별도 프로세스**입니다.
> 본 프로젝트와 동일하게 Spring Legacy(5.3) + MyBatis로 구성했고, Spring Boot는 쓰지 않습니다.

---

## 실행

앱은 Docker 이미지로 빌드해서 띄웁니다(`Dockerfile`: Gradle로 WAR 빌드 → Tomcat 9 이미지에
`ROOT.war`로 배포). DB는 컨테이너에 같이 띄우지 않고 **이미 떠 있는 MySQL 8.4 서버**에 직접
붙습니다 — 접속정보·비밀키 등 민감한 값은 전부 `.env`로 주입합니다.

1. `.env.example`을 복사해 `.env`를 만들고 실제 값을 채웁니다(MySQL 접속정보, systemKey, 기본
   client_id/secret). `.env`는 git에 커밋되지 않습니다.

   ```bash
   cp .env.example .env
   ```

2. MySQL에 `.env`의 `DB_NAME`과 같은 이름의 빈 스키마를 미리 만들어둡니다(테이블 자체는 앱이
   기동하면서 Flyway가 자동으로 만듭니다).

   ```sql
   CREATE DATABASE mockbroker CHARACTER SET utf8mb4;
   ```

3. 빌드하고 띄웁니다.

   ```bash
   docker compose up -d --build
   docker compose logs -f   # 기동 로그 확인
   ```

기동되면 아래 주소로 접속합니다.

| 주소 | 용도 |
|---|---|
| http://localhost:8080/ | 관제 콘솔 (아이디/비밀번호로 로그인해서 조작) |
| http://localhost:8080/connections.html | 관제 콘솔 (관리자 로그인, 커넥션 현황 + 유저에게 시나리오 적용) |
| http://localhost:8080/mock/auth/login | 로그인 (외부 연동 요청의 진입점) |
| http://localhost:8080/v2/invest/accounts | 마이데이터 규격 API |

GitHub Actions(`.github/workflows/deploy.yml`)가 `main` 브랜치 푸시마다 이미지를 빌드해
GHCR에 올리고, 배포 서버에 SSH로 접속해 `docker compose pull && up -d`로 갈아끼웁니다. 배포
서버 쪽에는 이 저장소의 `docker-compose.yml`과 `.env`가 미리 있어야 하고, GitHub 저장소
Settings → Secrets에 `DEPLOY_HOST`/`DEPLOY_USER`/`DEPLOY_SSH_KEY`/`DEPLOY_PATH`를 등록해야
합니다.

---

## 데이터가 사는 곳

런타임 상태는 **MySQL 8.4**에, 초기 데이터는 **`resources/scenarios/*.json`**에 있습니다.
스키마는 `resources/db/migration/`의 Flyway 마이그레이션으로 관리되며, 앱이 기동할 때마다
`ScenarioService`가 자동으로 적용합니다(이미 적용된 버전은 건너뜀). 완전히 처음 상태로
되돌리려면 MySQL에서 이 스키마의 테이블을 직접 비우면 됩니다.

각 데모 유저의 데이터는 `profile_code` 컬럼으로 완전히 분리되어 있어, 여러 유저가 동시에
로그인해 있어도 서로의 계좌·시세·거래에 전혀 영향을 주지 않습니다.

| 테이블 | 내용 |
|---|---|
| `mock_user` | 데모 유저의 로그인 자격증명(해시)·연결정보 |
| `mock_account` | 계좌와 예수금 |
| `mock_holding` | 종목별 보유수량·평균단가 |
| `mock_transaction` | 거래내역 |
| `mock_price` | 종목 현재가 |

`resources/kospi_stocks.json`에 **코스피 전 종목(우선주 포함, 약 940여개)**의 종목코드·종목명이
번들되어 있는데, 이건 유저 앞으로 미리 깔아두는 게 아니다 — 유저 한 명당 900여개씩 매번 실시간
조회하며 저장하면 로그인·회원가입·초기화가 한참 걸리기 때문이다. 대신:

- `mock_price`에는 **실제로 조회되거나 거래된 종목만** 들어간다. 처음 보는 종목의 현재가를
  조회하면(`GET /mock/prices/{prodCode}`, 콘솔에서 종목을 고르는 순간 자동 호출됨) 그 순간
  네이버에서 실시간으로 가져와 그 유저 앞으로 딱 한 번 활성화되고, 이후로는 저장된 값을 그대로
  쓴다 (`StockMasterService.ensureActivated`).
  주문(`/mock/orders`)도 아직 활성화 안 된 종목이면 같은 방식으로 그 자리에서 활성화한 뒤 체결한다.
- 콘솔의 종목 선택창(`GET /mock/products`)은 이 944개 코드·이름 목록을 그대로 보여준다 —
  네트워크 호출 없이 즉시 뜬다.
- `POST /mock/prices/refresh`는 **보유 중인 종목만** 네이버 실시간 시세로 다시 맞춘다 (활성화된
  전체 목록이 아니라 보유 종목으로 범위를 좁혀 빠르게 응답한다).

---

## 데모 유저

로그인 자격증명은 더 이상 git에 커밋되는 파일에 들어있지 않습니다. 서버가 부팅해도 유저를
자동으로 만들지 않습니다 — 흐름은 이렇게 나뉩니다.

1. **유저 생성**: `POST /mock/auth/register`로 `loginId`/`loginPassword`를 정해서 계정을 만듭니다
   (`systemKey`는 `.env`의 `MOCKBROKER_SIGNUP_KEY`).
2. **거래 패턴 적용**: 관제 콘솔(`/connections.html`)에 관리자로 로그인해서, 그 `loginId`를 골라
   번들된 템플릿(성장주 집중형/추격매수형/물타기 반복형 등) 중 하나를 적용하거나, JSON을 직접
   입력합니다. 기존 계좌·시세·거래이력은 지워지고 그 정의로 새로 만들어집니다.

관제 콘솔에 로그인한 **관리자**라면 이 두 단계를 굳이 나눠서 할 필요 없이, 콘솔의 "유저 생성"
카드(또는 `POST /mock/admin/users`)에서 `loginId`/`password`와 템플릿을 한 번에 넣어 유저 생성과
시나리오 적용을 한 번에 끝낼 수 있습니다 — `systemKey`도 필요 없습니다(이미 관리자 인증이라).
더 이상 필요 없는 테스트 유저는 콘솔의 "유저 관리" 카드(또는 `DELETE /mock/admin/users/{loginId}`)
에서 삭제할 수 있으며, 그 유저의 계좌·거래내역·커넥션·접근토큰까지 전부 같이 정리됩니다.

직접 입력한 시나리오 JSON을 다음에도 재사용하고 싶다면, 관제 콘솔에서 "템플릿으로 저장"을
체크하거나 `POST /mock/admin/templates`를 호출해 DB에 커스텀 템플릿으로 저장해둘 수 있습니다
(파일로 번들된 기본 템플릿과 별개로 관리되며, `DELETE /mock/admin/templates/{templateId}`로
지울 수 있습니다 — 번들 템플릿 자체는 삭제할 수 없습니다).

`resources/scenarios/*.json`은 이제 "거래 패턴 템플릿"일 뿐이라 로그인 자격증명이 없고,
안전하게 git에 커밋됩니다.

| templateId | 성향 | 특징 |
|---|---|---|
| `GROWTH_FOCUS_01` | 성장주 집중형 | 매도가 거의 없고 장기 보유, 계좌 2개(위탁·ISA) |
| `MOMENTUM_CHASER_01` | 추격매수형 | 매수 후 1~2주 내 손실 확정이 반복됨 |
| `AVERAGING_DOWN_01` | 물타기 반복형 | 한 종목을 4회 추가 매수, 예수금 소진 |

`POST /mock/reset`은 그 유저에게 **마지막으로 적용된 시나리오**를 다시 재생해서 초기 상태로
되돌립니다(적용 이력은 `mock_user.scenario_json`에 저장됨). 아직 시나리오를 한 번도 적용받지
않은 유저는 되돌릴 대상이 없어 `40001`이 납니다.

### 새 템플릿 추가하기

`resources/scenarios/`에 JSON 파일 하나만 넣으면 관제 콘솔의 템플릿 선택창에 자동으로 뜹니다.
**자바 코드는 건드리지 않습니다.**

```json
{
  "templateId": "MY_SCENARIO_01",
  "profileName": "분산투자형",
  "description": "여러 업종에 고르게 나눠 담는 유형",
  "orgCode": "S9990003A",
  "orgName": "테스트증권(모의)",
  "prices": [
    { "prodCode": "005930", "prodName": "삼성전자", "marketType": "KOSPI", "currentPrice": 84200 }
  ],
  "accounts": [
    { "accountNum": "5019999999", "accountName": "종합위탁계좌",
      "accountType": "101", "issueDate": "20250101", "initialCash": 10000000 }
  ],
  "trades": [
    { "accountNum": "5019999999", "tradedAt": "20260115103322",
      "side": "BUY", "prodCode": "005930", "quantity": 10, "price": 74800 }
  ]
}
```

`templateId`는 다른 파일과만 겹치지 않으면 됩니다(실제 유저의 `profile_code`와는 무관).
보유종목과 예수금은 적지 않습니다 — `trades`를 시간순으로 재생하면 체결 로직이 그대로
계산해주기 때문에, 작성자는 **거래 이력만** 쓰면 나머지가 자동으로 맞춰집니다. 다만 재생 중
예수금이나 보유수량이 모자라면 적용에 실패하므로, `initialCash`는 넉넉히 잡으세요.

---

## API

### 마이데이터 규격 API (`/v2/invest/**`)

공통 요청 헤더가 필요합니다.

```
Authorization: Bearer {접근토큰}
x-api-tran-id: {거래고유번호}
x-api-type: {정기적/비정기적 구분}
```

| 규격 ID | 메서드 | 경로 | 설명 |
|---|---|---|---|
| 금투-001 | GET | `/v2/invest/accounts` | 계좌 목록 조회 |
| 금투-002 | POST | `/v2/invest/accounts/basic` | 계좌 기본정보(예수금 등) |
| 금투-003 | POST | `/v2/invest/accounts/transactions` | 거래내역 조회 (거래일시 내림차순) |
| 금투-004 | POST | `/v2/invest/accounts/products` | 보유 상품정보 조회 |

```bash
curl -X POST http://localhost:8080/v2/invest/accounts/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H "x-api-tran-id: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"org_code":"S9990001A","account_num":"5011234567",
       "from_date":"20260101","to_date":"20261231","limit":100}'
```

### 로그인 (`/mock/auth/login`)

외부 연동 요청의 진입점입니다. 실제 마이데이터의 통합인증·개별인증을 흉내낸 것으로, 인증이
필요 없습니다 (당연히 로그인 전이니까요).

```bash
curl -X POST http://localhost:8080/mock/auth/login \
  -H "Content-Type: application/json" \
  -d '{"loginId":"demo1","loginPassword":"1234"}'
```

```json
{
  "connectionId": "b3f5c2a1...",
  "accessToken": "9f2e7d0b...",
  "tokenType": "Bearer",
  "orgCode": "S9990001A",
  "orgName": "미래에셋증권(모의)",
  "accounts": [ { "accountNum": "5011234567", "accountName": "종합위탁계좌", ... } ]
}
```

같은 유저로 다시 로그인해도 데이터는 재시드되지 않고, 발급된 토큰도 그대로 재사용됩니다
(다른 유저의 연결에는 영향 없음). `connectionId`는 그 유저에게 고정된 값이라, Investory가
재로그인 이후에도 "같은 사람"임을 추적하는 안정적인 키로 쓸 수 있습니다.

### 회원가입 (`/mock/auth/register`)

자유롭게 새 계정을 하나 만들고 싶을 때 씁니다 — 관제 콘솔에서 시나리오를 적용하려면 먼저
이걸로 유저부터 만들어야 합니다. `systemKey`가
맞아야만 가입되는데, 이건 진짜 보안장치가 아니라 아무나 계정을 마구 만들지 못하게 막는
최소한의 문입니다. 값은 `.env`의 `MOCKBROKER_SIGNUP_KEY`로 정합니다.

```bash
curl -X POST http://localhost:8080/mock/auth/register \
  -H "Content-Type: application/json" \
  -d '{"loginId":"myaccount","loginPassword":"mypw1234","systemKey":"<.env의 MOCKBROKER_SIGNUP_KEY 값>"}'
```

가입에 성공하면 로그인과 동일한 형태로 `connectionId`/`accessToken`을 바로 내려줍니다
(별도로 로그인을 한 번 더 할 필요 없음). 계좌 1개(`9000000001`, 예수금 1,000만원)와
기본 종목 시세 몇 개(삼성전자·SK하이닉스·NAVER·LG에너지솔루션·한미반도체)가 자동으로
세팅되어, 가입 직후 바로 `/mock/orders`로 거래를 시작할 수 있습니다. `loginId`가
이미 있으면 `40001`로 거부됩니다.

### 데모 조작 API (`/mock/**`)

규격에 없는, 이 서버가 자체적으로 추가한 엔드포인트입니다. `/mock/auth/**`를 제외한
나머지는 모두 로그인해서 받은 `Authorization: Bearer {accessToken}`이 필요하며, 그 토큰이
가리키는 유저의 데이터만 조회·조작합니다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/mock/auth/login` | 로그인 (인증 불필요) |
| POST | `/mock/auth/register` | 회원가입 (인증 불필요, systemKey 필요) |
| POST | `/mock/orders` | 매수·매도 주문 (요청 즉시 체결, 과거 거래 소급 등록 가능) |
| POST | `/mock/reset` | 로그인된 유저의 데이터만 초기 상태로 복구 |
| GET | `/mock/state` | 콘솔용 통합 상태 조회 |
| GET | `/mock/products` | 코스피 전 종목 코드·이름 목록 (네트워크 호출 없음) |
| GET | `/mock/prices/{prodCode}` | 종목 현재가 조회 (처음 보는 종목이면 그 자리에서 실시간 활성화) |
| POST | `/mock/prices` | 종목 현재가 조정 |
| POST | `/mock/prices/refresh` | 보유 종목의 현재가를 네이버 금융 시세로 다시 맞춤 |

```bash
# 매수 (price 생략 시 현재가로 체결)
curl -X POST http://localhost:8080/mock/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountNum":"5011234567","prodCode":"005930","side":"BUY","quantity":10}'

# 과거 거래를 소급 등록 (tradedAt: yyyyMMddHHmmss). 가격제한폭 검사를 받지 않고,
# 종목 현재가도 갱신하지 않는다 — 콘솔에서는 "과거 거래로 등록" 체크박스로 켤 수 있다.
curl -X POST http://localhost:8080/mock/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountNum":"5011234567","prodCode":"005930","side":"BUY","quantity":10,
       "price":74800,"tradedAt":"20250115103322"}'
```

---

## 규격에서 벗어난 부분

실제 규격을 그대로 따르지 않고 데모 목적에 맞게 단순화한 지점들입니다.

| 항목 | 실제 규격 | 이 서버 |
|---|---|---|
| 인증 | 통합인증·개별인증을 거쳐 토큰 발급 | `loginId`/`loginPassword`로 `/mock/auth/login` 한 번이면 토큰 발급 |
| `trans_type` | [첨부6] 거래종류 코드표 참조 | `101` 매수, `102` 매도 (자체 정의) |
| `account_type` | [첨부3] 계좌 구분 코드 | `101` 위탁종합, `102` ISA, `105` 연금 |
| `search_timestamp` | 증분 조회에 활용 | 값은 회신하되 필터링에는 쓰지 않음 |
| 장 초반·마감 응답거부 | 에러코드 `50010` | 구현하지 않음 |
| 수수료·세금 | 증권사별 상이 | 수수료 0.015%, 매도 제세금 0.15% 고정 |
| 가격제한폭 | 전일 종가 대비 ±30% (상한가/하한가) | 현재가 대비 ±30%로 단순화. 라이브 주문에만 적용, 시나리오 과거 거래 재생에는 미적용 |
| 매매 주문 | **마이데이터 범위 밖** | `/mock/orders` 로 자체 추가 |

응답 코드는 정상 `00000`, 그 외에는 아래를 사용합니다.

| rsp_code | 상황 |
|---|---|
| `40001` | 필수 파라미터 누락 등 잘못된 요청 |
| `40101` | 접근토큰 없음 또는 무효 |
| `40401` | 계좌·종목·시나리오를 찾을 수 없음 |
| `50020` | 예수금 부족 |
| `50021` | 보유수량 부족 |
| `50022` | 가격제한폭(상한가/하한가) 초과 |

---

## 체결가와 시세의 일관성

`/mock/orders`로 가격을 직접 지정할 수 있지만, 무제한으로 허용하면 체결가와 시세가
크게 벌어져 이후 성향분석·평가손익 계산이 왜곡될 수 있다. 그래서 두 가지 규칙을 둔다.

1. **가격제한폭 검증** — 요청 가격은 현재가 대비 ±30% 이내여야 한다 (실제 국내 증시의
   상한가/하한가 규칙을 단순화한 값). 벗어나면 `50022`로 거부된다.
2. **체결가가 곧 다음 현재가** — 주문이 체결되면 그 가격이 해당 종목의 새로운 현재가로
   갱신된다. 실제 시장에서 마지막 체결가가 현재가가 되는 것과 동일하다.

이 두 규칙 덕분에 콘솔에서 아무리 여러 번 주문을 넣어도 시세와 체결가가 항상 합리적인
범위 안에서 함께 움직인다. 단, **시나리오의 과거 거래 재생**에는 두 규칙 모두 적용하지 않는다.
시드 데이터는 여러 날짜에 걸친 실제 있었던 가격이므로 검증 대상이 아니며, 재생 중 현재가가
바뀌면 시나리오 작성자가 지정한 "오늘의 시세"가 마지막 재생 거래가로 덮어써지기 때문이다.

## Investory 본 서비스와 붙이기

```
① POST /mock/auth/login       → connectionId, accessToken, orgCode, 계좌 목록 확보
② GET  /v2/invest/accounts    → broker_connections, investment_accounts 적재
③ POST .../transactions       → trades 적재
④ POST .../products           → holding_snapshots 적재
```

Investory가 연동하는 실제 계정마다 다른 데모 유저(`loginId`)로 로그인시키면, 계정별로
서로 다른 목업 데이터(성향)가 붙습니다 — 여러 유저가 동시에 로그인해 있어도 서로 섞이지
않습니다. `connectionId`를 그 계정의 연동 식별자로 저장해두면, 이후 재로그인 때마다
accessToken이 바뀌어도 "같은 연동"임을 추적할 수 있습니다.

거래 중복 적재를 막는 키는 응답의 **`trans_no`**(계좌·일자별 일련번호, 예 `20260112-0001`)입니다.
이 값을 `trades.external_trade_id`에 그대로 넣으면 `uk_trade_account_external` 제약이
재동기화를 여러 번 돌려도 같은 거래가 두 번 쌓이지 않게 막아줍니다.

신규 거래를 확인하는 순서는 이렇습니다.

1. 관제 콘솔에서 매수 버튼 클릭 → 체결 테이프에 거래가 즉시 나타남
2. Investory 앱에서 **재동기화** 실행 → 금투-003/004 호출
3. 거래 타임라인과 보유종목 현황에 방금 만든 거래가 반영됨

자동화된 연동 테스트(스크립트/CI)에서는 콘솔 클릭 대신 `POST /mock/test/trade`를 쓰면 된다.
로그인(accessToken 발급) 단계 없이 `loginId`만으로 즉시 체결시킬 수 있어서, "① 목 서버에
거래 발생 → ② 메인 서버 동기화 API 호출 → ③ 메인 서버 DB 확인"을 curl 두 번으로 재현할 수
있다. 자세한 요청/응답 형식은 [API.md](API.md#post-mocktesttrade--loginid-기반-즉시-체결) 참고.

동기화 시 `trades`만 넣지 말고 **당일 `holding_snapshots`도 함께 재계산**해야
보유종목 화면의 평가금액이 맞습니다.
