package com.investory.mockbroker.domain;

import java.math.BigDecimal;

public class MockTransaction {
    private Long seq;
    private String profileCode;
    private String accountNum;
    private String transNo;
    private String transDtime;
    private String transType;
    private String transTypeDetail;
    private String prodCode;
    private String prodName;
    private BigDecimal transNum;
    private String transUnit;
    private BigDecimal baseAmt;
    private BigDecimal transAmt;
    private BigDecimal settleAmt;
    private BigDecimal balanceAmt;
    private String currencyCode;
    private String exCode;

    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }
    public String getProfileCode() { return profileCode; }
    public void setProfileCode(String profileCode) { this.profileCode = profileCode; }
    public String getAccountNum() { return accountNum; }
    public void setAccountNum(String accountNum) { this.accountNum = accountNum; }
    public String getTransNo() { return transNo; }
    public void setTransNo(String transNo) { this.transNo = transNo; }
    public String getTransDtime() { return transDtime; }
    public void setTransDtime(String transDtime) { this.transDtime = transDtime; }
    public String getTransType() { return transType; }
    public void setTransType(String transType) { this.transType = transType; }
    public String getTransTypeDetail() { return transTypeDetail; }
    public void setTransTypeDetail(String transTypeDetail) { this.transTypeDetail = transTypeDetail; }
    public String getProdCode() { return prodCode; }
    public void setProdCode(String prodCode) { this.prodCode = prodCode; }
    public String getProdName() { return prodName; }
    public void setProdName(String prodName) { this.prodName = prodName; }
    public BigDecimal getTransNum() { return transNum; }
    public void setTransNum(BigDecimal transNum) { this.transNum = transNum; }
    public String getTransUnit() { return transUnit; }
    public void setTransUnit(String transUnit) { this.transUnit = transUnit; }
    public BigDecimal getBaseAmt() { return baseAmt; }
    public void setBaseAmt(BigDecimal baseAmt) { this.baseAmt = baseAmt; }
    public BigDecimal getTransAmt() { return transAmt; }
    public void setTransAmt(BigDecimal transAmt) { this.transAmt = transAmt; }
    public BigDecimal getSettleAmt() { return settleAmt; }
    public void setSettleAmt(BigDecimal settleAmt) { this.settleAmt = settleAmt; }
    public BigDecimal getBalanceAmt() { return balanceAmt; }
    public void setBalanceAmt(BigDecimal balanceAmt) { this.balanceAmt = balanceAmt; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public String getExCode() { return exCode; }
    public void setExCode(String exCode) { this.exCode = exCode; }
}
