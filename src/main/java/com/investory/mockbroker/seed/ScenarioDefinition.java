package com.investory.mockbroker.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * scenarios/*.json 한 개에 대응하는 "거래 패턴" 템플릿 — 로그인 자격증명은 담지 않는다.
 * 관리자 콘솔에서 기존 유저를 골라 이 템플릿을 적용하면, 그 유저 앞으로 계좌·시세·거래이력이
 * 통째로 (재)생성된다. templateId는 파일 하나를 가리키는 식별자일 뿐 실제 유저의 profile_code와는
 * 무관하다.
 *
 * 보유종목과 예수금은 파일에 직접 적지 않는다. trades 를 시간순으로 재생하면
 * 체결 로직이 그대로 계산해주므로, 작성자는 거래 이력만 쓰면 된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioDefinition {

    private String templateId;
    private String profileName;
    private String description;
    private String orgCode;
    private String orgName;
    private List<PriceSeed> prices = new ArrayList<>();
    private List<AccountSeed> accounts = new ArrayList<>();
    private List<TradeSeed> trades = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceSeed {
        private String prodCode;
        private String prodName;
        private String prodType = "401";
        private String exCode = "FRK";
        private String marketType = "KOSPI";
        private BigDecimal currentPrice;

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountSeed {
        private String accountNum;
        private String accountName;
        private String accountType = "101";
        private String issueDate;
        /** 거래 재생 전의 시작 예수금 */
        private BigDecimal initialCash = BigDecimal.ZERO;

        public String getAccountNum() { return accountNum; }
        public void setAccountNum(String accountNum) { this.accountNum = accountNum; }
        public String getAccountName() { return accountName; }
        public void setAccountName(String accountName) { this.accountName = accountName; }
        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }
        public String getIssueDate() { return issueDate; }
        public void setIssueDate(String issueDate) { this.issueDate = issueDate; }
        public BigDecimal getInitialCash() { return initialCash; }
        public void setInitialCash(BigDecimal initialCash) { this.initialCash = initialCash; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TradeSeed {
        private String accountNum;
        /** yyyyMMddHHmmss */
        private String tradedAt;
        /** BUY 또는 SELL */
        private String side;
        private String prodCode;
        private BigDecimal quantity;
        private BigDecimal price;

        public String getAccountNum() { return accountNum; }
        public void setAccountNum(String accountNum) { this.accountNum = accountNum; }
        public String getTradedAt() { return tradedAt; }
        public void setTradedAt(String tradedAt) { this.tradedAt = tradedAt; }
        public String getSide() { return side; }
        public void setSide(String side) { this.side = side; }
        public String getProdCode() { return prodCode; }
        public void setProdCode(String prodCode) { this.prodCode = prodCode; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }

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
    public List<PriceSeed> getPrices() { return prices; }
    public void setPrices(List<PriceSeed> prices) { this.prices = prices; }
    public List<AccountSeed> getAccounts() { return accounts; }
    public void setAccounts(List<AccountSeed> accounts) { this.accounts = accounts; }
    public List<TradeSeed> getTrades() { return trades; }
    public void setTrades(List<TradeSeed> trades) { this.trades = trades; }
}
