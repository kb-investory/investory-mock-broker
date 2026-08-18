-- 이 목업 서버가 쓰는 전체 스키마. 메인 서비스와 DB를 통일해서 쓰기로 하면서 Flyway를 걷어냈고,
-- 이제 이 파일이 스키마의 유일한 소스다 — 새 테이블/컬럼이 필요하면 여기에 직접 추가한다.

CREATE TABLE mock_user (
    profile_code         VARCHAR(40)  NOT NULL,
    login_id             VARCHAR(60)  NOT NULL,
    login_password_hash  VARCHAR(100) NOT NULL,
    connection_id        VARCHAR(40)  NOT NULL,
    org_code             VARCHAR(10)  NOT NULL,
    org_name             VARCHAR(60)  NOT NULL,
    scenario_json        LONGTEXT     NULL,
    PRIMARY KEY (profile_code),
    CONSTRAINT uk_user_login_id UNIQUE (login_id),
    CONSTRAINT uk_user_connection_id UNIQUE (connection_id)
);

CREATE TABLE mock_account (
    profile_code  VARCHAR(40)  NOT NULL,
    org_code      VARCHAR(10)  NOT NULL,
    account_num   VARCHAR(20)  NOT NULL,
    account_name  VARCHAR(60)  NOT NULL,
    account_type  VARCHAR(3)   NOT NULL,
    issue_date    VARCHAR(8)   NOT NULL,
    cash_balance  DECIMAL(18,3) NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_code, account_num)
);

CREATE TABLE mock_price (
    profile_code  VARCHAR(40)  NOT NULL,
    prod_code     VARCHAR(12)  NOT NULL,
    prod_name     VARCHAR(300) NOT NULL,
    prod_type     VARCHAR(3)   NOT NULL DEFAULT '401',
    ex_code       VARCHAR(3)   NOT NULL DEFAULT 'FRK',
    market_type   VARCHAR(10)  NOT NULL DEFAULT 'KOSPI',
    current_price DECIMAL(20,8) NOT NULL,
    PRIMARY KEY (profile_code, prod_code)
);

CREATE TABLE mock_holding (
    profile_code VARCHAR(40)  NOT NULL,
    account_num  VARCHAR(20)  NOT NULL,
    prod_code    VARCHAR(12)  NOT NULL,
    holding_num  DECIMAL(21,8) NOT NULL,
    avg_price    DECIMAL(20,8) NOT NULL,
    PRIMARY KEY (profile_code, account_num, prod_code)
);

CREATE TABLE mock_transaction (
    seq               BIGINT AUTO_INCREMENT,
    profile_code      VARCHAR(40)  NOT NULL,
    account_num       VARCHAR(20)  NOT NULL,
    trans_no          VARCHAR(64)  NOT NULL,
    trans_dtime       VARCHAR(14)  NOT NULL,
    trans_type        VARCHAR(3)   NOT NULL,
    trans_type_detail VARCHAR(60)  NOT NULL,
    prod_code         VARCHAR(12),
    prod_name         VARCHAR(60),
    trans_num         DECIMAL(21,8),
    trans_unit        VARCHAR(30),
    base_amt          DECIMAL(20,8),
    trans_amt         DECIMAL(18,3),
    settle_amt        DECIMAL(18,3),
    balance_amt       DECIMAL(18,3),
    currency_code     VARCHAR(3)   NOT NULL DEFAULT 'KRW',
    ex_code           VARCHAR(3),
    PRIMARY KEY (seq),
    CONSTRAINT uk_txn_account_no UNIQUE (profile_code, account_num, trans_no)
);

CREATE INDEX idx_txn_account_dtime ON mock_transaction (profile_code, account_num, trans_dtime);

-- 외부 서비스(client)가 client_id/client_secret으로 자신을 증명하고, 유저와의 커넥션을 맺어
-- 이후 요청에서 그 유저 데이터에 접근하는 두 번째 인증 경로용 테이블.
CREATE TABLE mock_client (
    client_id      VARCHAR(60)  NOT NULL,
    client_secret  VARCHAR(100) NOT NULL,
    client_name    VARCHAR(100) NOT NULL,
    PRIMARY KEY (client_id)
);

CREATE TABLE mock_connection (
    connection_id  VARCHAR(40)  NOT NULL,
    client_id      VARCHAR(60)  NOT NULL,
    profile_code   VARCHAR(40)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (connection_id),
    CONSTRAINT uk_connection_client_profile UNIQUE (client_id, profile_code)
);

-- 관제 콘솔에서 관리자가 직접 저장한 커스텀 시나리오 템플릿. resources/scenarios/*.json 번들
-- 파일 템플릿과 별개로 DB에 저장되며, GET /mock/admin/templates에서 파일 템플릿과 합쳐 반환된다.
CREATE TABLE mock_scenario_template (
    template_id     VARCHAR(60)  NOT NULL,
    profile_name    VARCHAR(100) NOT NULL,
    description     VARCHAR(300),
    definition_json LONGTEXT     NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (template_id)
);

-- 증권사(org) 목록을 시나리오 JSON이 아니라 DB에 둔다. 새 증권사를 추가할 때 배포 없이
-- 관제 콘솔에서 바로 등록할 수 있게 하기 위함.
CREATE TABLE mock_org (
    org_code  VARCHAR(10) NOT NULL,
    org_name  VARCHAR(60) NOT NULL,
    PRIMARY KEY (org_code)
);

INSERT INTO mock_org (org_code, org_name) VALUES
    ('S9990001A', '미래에셋증권(모의)'),
    ('S9990002A', '키움증권(모의)'),
    ('S9990099A', '테스트증권(모의)');
