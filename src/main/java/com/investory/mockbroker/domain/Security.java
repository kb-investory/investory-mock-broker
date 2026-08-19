package com.investory.mockbroker.domain;

/** 메인 서비스와 공유하는 securities 테이블(종목 마스터)의 읽기 전용 뷰. */
public class Security {
    private String securityCode;
    private String securityName;
    private String marketType;

    public String getSecurityCode() { return securityCode; }
    public void setSecurityCode(String securityCode) { this.securityCode = securityCode; }
    public String getSecurityName() { return securityName; }
    public void setSecurityName(String securityName) { this.securityName = securityName; }
    public String getMarketType() { return marketType; }
    public void setMarketType(String marketType) { this.marketType = marketType; }
}
