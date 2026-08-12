package com.investory.mockbroker.dto;

import com.investory.mockbroker.seed.ScenarioDefinition;

/**
 * POST /mock/admin/scenarios 요청. templateId(번들된 템플릿 선택) 또는 scenario(직접 입력한
 * JSON) 중 하나만 채우면 된다 — 둘 다 있으면 templateId가 우선한다.
 */
public class ApplyScenarioRequest {
    private String loginId;
    private String templateId;
    private ScenarioDefinition scenario;

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public ScenarioDefinition getScenario() { return scenario; }
    public void setScenario(ScenarioDefinition scenario) { this.scenario = scenario; }
}
