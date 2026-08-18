package com.investory.mockbroker.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /mock/admin/templates/generate 요청. 종목별 실제 과거 시세(네이버 일별시세)를 가져와
 * 그 가격으로 매수 거래를 채운 시나리오 템플릿을 만든다 — 주로 본 서비스 분석 기능이 요구하는
 * "최소 N일치 거래이력" 테스트 데이터를 손으로 가격을 지어내지 않고 준비하기 위함이다.
 */
public class GenerateScenarioRequest {
    private String templateId;
    private String profileName;
    private String description;
    private String orgCode;
    private String orgName;
    private String accountNum;
    private String accountName = "종합위탁계좌";
    private String accountType = "101";
    private BigDecimal initialCash;
    /** 오늘로부터 며칠 전까지 거슬러 올라가 거래를 채울지. 기본 90일. */
    private Integer days;
    /** 비우면 현재 시가총액 순위(코스피 상위 20 + 코스닥 상위 10)를 실시간 조회해 기본값으로 쓴다. */
    private List<String> prodCodes;
    /** 종목 하나당 몇 번 매수할지. 기본 4회, 기간 전체에 고르게 분산시킨다. */
    private Integer tradesPerProduct;

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOrgCode() { return orgCode; }
    public void setOrgCode(String orgCode) { this.orgCode = orgCode; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getAccountNum() { return accountNum; }
    public void setAccountNum(String accountNum) { this.accountNum = accountNum; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public BigDecimal getInitialCash() { return initialCash; }
    public void setInitialCash(BigDecimal initialCash) { this.initialCash = initialCash; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public List<String> getProdCodes() { return prodCodes; }
    public void setProdCodes(List<String> prodCodes) { this.prodCodes = prodCodes; }
    public Integer getTradesPerProduct() { return tradesPerProduct; }
    public void setTradesPerProduct(Integer tradesPerProduct) { this.tradesPerProduct = tradesPerProduct; }
}
