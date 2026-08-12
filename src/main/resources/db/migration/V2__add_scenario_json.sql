-- 관리자 콘솔에서 이 유저에게 마지막으로 적용한 시나리오(템플릿 그대로거나 직접 입력한 JSON)를
-- 저장해둔다. /mock/reset이 이 값을 다시 재생해서 초기 상태로 되돌린다.
ALTER TABLE mock_user ADD COLUMN scenario_json LONGTEXT NULL;
