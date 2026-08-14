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
