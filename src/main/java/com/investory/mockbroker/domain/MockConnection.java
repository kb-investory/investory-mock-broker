package com.investory.mockbroker.domain;

public class MockConnection {
    private String connectionId;
    private String clientId;
    private String profileCode;
    /** insert 시에는 안 쓴다 — 목록 조회(findByClientId)에서만 채워진다. */
    private String createdAt;

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getProfileCode() { return profileCode; }
    public void setProfileCode(String profileCode) { this.profileCode = profileCode; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
