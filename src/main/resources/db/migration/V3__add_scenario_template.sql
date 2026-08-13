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
