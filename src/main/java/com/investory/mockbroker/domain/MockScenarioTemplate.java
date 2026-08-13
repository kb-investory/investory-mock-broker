package com.investory.mockbroker.domain;

public class MockScenarioTemplate {
    private String templateId;
    private String profileName;
    private String description;
    private String definitionJson;

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDefinitionJson() { return definitionJson; }
    public void setDefinitionJson(String definitionJson) { this.definitionJson = definitionJson; }
}
