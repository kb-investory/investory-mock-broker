package com.investory.mockbroker.dto;

import java.math.BigDecimal;

/** 체결 결과. 콘솔 화면과 주문 API 응답에 함께 쓰인다. */
public class OrderResult {
    private String accountNum;
    private String transNo;
    private String transDtime;
    private String side;
    private String prodCode;
    private String prodName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal transAmt;
    private BigDecimal commission;
    private BigDecimal tax;
    private BigDecimal settleAmt;
    private BigDecimal cashBalance;
    private BigDecimal holdingNum;
    private BigDecimal avgPrice;

    public String getAccountNum() { return accountNum; }
    public void setAccountNum(String accountNum) { this.accountNum = accountNum; }
    public String getTransNo() { return transNo; }
    public void setTransNo(String transNo) { this.transNo = transNo; }
    public String getTransDtime() { return transDtime; }
    public void setTransDtime(String transDtime) { this.transDtime = transDtime; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getProdCode() { return prodCode; }
    public void setProdCode(String prodCode) { this.prodCode = prodCode; }
    public String getProdName() { return prodName; }
    public void setProdName(String prodName) { this.prodName = prodName; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTransAmt() { return transAmt; }
    public void setTransAmt(BigDecimal transAmt) { this.transAmt = transAmt; }
    public BigDecimal getCommission() { return commission; }
    public void setCommission(BigDecimal commission) { this.commission = commission; }
    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }
    public BigDecimal getSettleAmt() { return settleAmt; }
    public void setSettleAmt(BigDecimal settleAmt) { this.settleAmt = settleAmt; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }
    public BigDecimal getHoldingNum() { return holdingNum; }
    public void setHoldingNum(BigDecimal holdingNum) { this.holdingNum = holdingNum; }
    public BigDecimal getAvgPrice() { return avgPrice; }
    public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }
}
