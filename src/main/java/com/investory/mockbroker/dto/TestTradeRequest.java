package com.investory.mockbroker.dto;

import java.math.BigDecimal;

/** /mock/test/trade 전용 요청. loginId로 유저를 지목하는 것 말고는 OrderRequest와 동일하다. */
public class TestTradeRequest {
    private String loginId;
    /** 생략하면 이 유저의 첫 번째 계좌를 쓴다. */
    private String accountNum;
    private String prodCode;
    /** BUY 또는 SELL */
    private String side;
    private BigDecimal quantity;
    /** 생략하면 현재가로 체결된다. */
    private BigDecimal price;
    private String tradedAt;

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getAccountNum() { return accountNum; }
    public void setAccountNum(String accountNum) { this.accountNum = accountNum; }
    public String getProdCode() { return prodCode; }
    public void setProdCode(String prodCode) { this.prodCode = prodCode; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getTradedAt() { return tradedAt; }
    public void setTradedAt(String tradedAt) { this.tradedAt = tradedAt; }
}
