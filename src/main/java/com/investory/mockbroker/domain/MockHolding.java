package com.investory.mockbroker.domain;

import java.math.BigDecimal;

public class MockHolding {
    private String profileCode;
    private String accountNum;
    private String prodCode;
    private BigDecimal holdingNum;
    private BigDecimal avgPrice;

    /** 조인으로 채워지는 종목 표시 정보 (mock_price 기준) */
    private String prodName;
    private String prodType;
    private String exCode;
    private BigDecimal currentPrice;

    public String getProfileCode() { return profileCode; }
    public void setProfileCode(String profileCode) { this.profileCode = profileCode; }
    public String getAccountNum() { return accountNum; }
    public void setAccountNum(String accountNum) { this.accountNum = accountNum; }
    public String getProdCode() { return prodCode; }
    public void setProdCode(String prodCode) { this.prodCode = prodCode; }
    public BigDecimal getHoldingNum() { return holdingNum; }
    public void setHoldingNum(BigDecimal holdingNum) { this.holdingNum = holdingNum; }
    public BigDecimal getAvgPrice() { return avgPrice; }
    public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }
    public String getProdName() { return prodName; }
    public void setProdName(String prodName) { this.prodName = prodName; }
    public String getProdType() { return prodType; }
    public void setProdType(String prodType) { this.prodType = prodType; }
    public String getExCode() { return exCode; }
    public void setExCode(String exCode) { this.exCode = exCode; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
}
