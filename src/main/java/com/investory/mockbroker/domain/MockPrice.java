package com.investory.mockbroker.domain;

import java.math.BigDecimal;

public class MockPrice {
    private String profileCode;
    private String prodCode;
    private String prodName;
    private String prodType;
    private String exCode;
    private String marketType;
    private BigDecimal currentPrice;

    public String getProfileCode() { return profileCode; }
    public void setProfileCode(String profileCode) { this.profileCode = profileCode; }
    public String getProdCode() { return prodCode; }
    public void setProdCode(String prodCode) { this.prodCode = prodCode; }
    public String getProdName() { return prodName; }
    public void setProdName(String prodName) { this.prodName = prodName; }
    public String getProdType() { return prodType; }
    public void setProdType(String prodType) { this.prodType = prodType; }
    public String getExCode() { return exCode; }
    public void setExCode(String exCode) { this.exCode = exCode; }
    public String getMarketType() { return marketType; }
    public void setMarketType(String marketType) { this.marketType = marketType; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
}
