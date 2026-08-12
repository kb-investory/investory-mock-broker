package com.investory.mockbroker.domain;

public class MockUser {
    private String profileCode;
    private String loginId;
    private String loginPasswordHash;
    private String connectionId;
    private String orgCode;
    private String orgName;
    /** 관리자 콘솔에서 마지막으로 적용한 시나리오 원본 JSON. 아직 없으면 null. */
    private String scenarioJson;

    public String getProfileCode() { return profileCode; }
    public void setProfileCode(String profileCode) { this.profileCode = profileCode; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getLoginPasswordHash() { return loginPasswordHash; }
    public void setLoginPasswordHash(String loginPasswordHash) { this.loginPasswordHash = loginPasswordHash; }
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getOrgCode() { return orgCode; }
    public void setOrgCode(String orgCode) { this.orgCode = orgCode; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getScenarioJson() { return scenarioJson; }
    public void setScenarioJson(String scenarioJson) { this.scenarioJson = scenarioJson; }
}
