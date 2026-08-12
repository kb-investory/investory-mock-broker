CREATE TABLE mock_user (
    profile_code         VARCHAR(40)  NOT NULL,
    login_id             VARCHAR(60)  NOT NULL,
    login_password_hash  VARCHAR(100) NOT NULL,
    connection_id        VARCHAR(40)  NOT NULL,
    org_code             VARCHAR(10)  NOT NULL,
    org_name             VARCHAR(60)  NOT NULL,
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
