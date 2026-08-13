package com.investory.mockbroker.dto;

import com.investory.mockbroker.seed.ScenarioDefinition;

/**
 * POST /mock/admin/users 요청. 유저를 만들고, templateId(번들 템플릿 선택) 또는 scenario(직접
 * 입력한 JSON)가 있으면 이어서 바로 적용까지 한 번에 한다 — 둘 다 있으면 templateId가 우선한다.
 * 회원가입(/mock/auth/register)과 달리 systemKey는 필요 없다 (이미 x-admin-token으로 인증됨).
 */
public class AdminCreateUserRequest {
    private String loginId;
    private String loginPassword;
    private String templateId;
    private ScenarioDefinition scenario;

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getLoginPassword() { return loginPassword; }
    public void setLoginPassword(String loginPassword) { this.loginPassword = loginPassword; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public ScenarioDefinition getScenario() { return scenario; }
    public void setScenario(ScenarioDefinition scenario) { this.scenario = scenario; }
}
