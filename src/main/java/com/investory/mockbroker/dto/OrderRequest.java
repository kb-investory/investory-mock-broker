package com.investory.mockbroker.dto;

import java.math.BigDecimal;

public class OrderRequest {
    private String accountNum;
    private String prodCode;
    /** BUY 또는 SELL */
    private String side;
    private BigDecimal quantity;
    /** 생략하면 현재가로 체결된다. */
    private BigDecimal price;
    /**
     * 과거 거래를 소급 등록할 때만 채운다 (yyyyMMddHHmmss). 생략하면 지금 시각의 라이브
     * 주문으로 처리되어 가격제한폭 검증을 받고 현재가를 갱신한다. 값이 있으면 과거 거래
     * 재생으로 취급해 두 가지 다 건드리지 않는다.
     */
    private String tradedAt;

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
