package com.investory.mockbroker.controller;

import com.investory.mockbroker.domain.MockAccount;
import com.investory.mockbroker.domain.MockHolding;
import com.investory.mockbroker.domain.MockTransaction;
import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.mapper.AccountMapper;
import com.investory.mockbroker.mapper.HoldingMapper;
import com.investory.mockbroker.mapper.TransactionMapper;
import com.investory.mockbroker.web.MyDataHeaderInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 마이데이터 금융투자 업권 정보제공 API 규격을 모방한 조회 엔드포인트.
 *
 * 구현 범위
 *   금투-001  GET  /v2/invest/accounts               계좌 목록 조회
 *   금투-002  POST /v2/invest/accounts/basic         계좌 기본정보 조회
 *   금투-003  POST /v2/invest/accounts/transactions  계좌 거래내역 조회
 *   금투-004  POST /v2/invest/accounts/products      계좌 상품정보 조회
 *
 * 응답 필드명은 규격의 snake_case를 그대로 쓰기 위해 LinkedHashMap으로 조립한다.
 */
@RestController
@RequestMapping("/v2/invest")
public class MyDataInvestController {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final TransactionMapper transactionMapper;

    @Autowired
    public MyDataInvestController(AccountMapper accountMapper, HoldingMapper holdingMapper,
                                  TransactionMapper transactionMapper) {
        this.accountMapper = accountMapper;
        this.holdingMapper = holdingMapper;
        this.transactionMapper = transactionMapper;
    }

    /** 금투-001 계좌 목록 조회 */
    @GetMapping("/accounts")
    public Map<String, Object> accounts(@RequestAttribute(MyDataHeaderInterceptor.PROFILE_CODE_ATTR) String profileCode,
                                        @RequestParam("org_code") String orgCode,
                                        @RequestParam(value = "limit", defaultValue = "500") int limit) {

        List<MockAccount> accounts = accountMapper.findByOrgCode(profileCode, orgCode);
        List<Map<String, Object>> list = new ArrayList<>();
        for (MockAccount a : accounts) {
            if (list.size() >= limit) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("account_num", a.getAccountNum());
            item.put("is_consent", true);
            item.put("account_name", a.getAccountName());
            item.put("account_type", a.getAccountType());
            item.put("issue_date", a.getIssueDate());
            item.put("is_tax_benefits", "105".equals(a.getAccountType()));
            item.put("is_cma", false);
            item.put("is_stock_trans", false);
            item.put("is_account_link", false);
            list.add(item);
        }

        Map<String, Object> body = success();
        body.put("search_timestamp", now());
        body.put("account_cnt", list.size());
        body.put("account_list", list);
        return body;
    }

    /** 금투-002 계좌 기본정보 조회 */
    @PostMapping("/accounts/basic")
    public Map<String, Object> accountBasic(@RequestAttribute(MyDataHeaderInterceptor.PROFILE_CODE_ATTR) String profileCode,
                                            @RequestBody Map<String, Object> request) {
        MockAccount account = requireAccount(profileCode, request);

        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("currency_code", "KRW");
        basic.put("withholdings_amt", account.getCashBalance());
        basic.put("credit_loan_amt", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
        basic.put("mortgage_amt", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
        basic.put("avail_balance", account.getCashBalance());

        List<Map<String, Object>> basicList = new ArrayList<>();
        basicList.add(basic);

        Map<String, Object> body = success();
        body.put("search_timestamp", now());
        body.put("base_date", LocalDate.now().format(DATE));
        body.put("basic_cnt", basicList.size());
        body.put("basic_list", basicList);
        return body;
    }

    /** 금투-003 계좌 거래내역 조회 (거래일시 내림차순) */
    @PostMapping("/accounts/transactions")
    public Map<String, Object> transactions(@RequestAttribute(MyDataHeaderInterceptor.PROFILE_CODE_ATTR) String profileCode,
                                            @RequestBody Map<String, Object> request) {
        MockAccount account = requireAccount(profileCode, request);

        String fromDate = asString(request.get("from_date"));
        String toDate = asString(request.get("to_date"));
        if (fromDate == null || toDate == null) {
            throw MockApiException.badRequest("from_date와 to_date는 필수입니다. (YYYYMMDD)");
        }

        int limit = asInt(request.get("limit"), 500);
        int offset = parseNextPage(asString(request.get("next_page")));

        List<MockTransaction> rows =
                transactionMapper.findByPeriod(profileCode, account.getAccountNum(), fromDate, toDate, limit, offset);
        int total = transactionMapper.countByPeriod(profileCode, account.getAccountNum(), fromDate, toDate);

        List<Map<String, Object>> list = new ArrayList<>();
        for (MockTransaction t : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("prod_name", t.getProdName());
            item.put("prod_code", t.getProdCode());
            item.put("trans_dtime", t.getTransDtime());
            item.put("trans_no", t.getTransNo());
            item.put("trans_type", t.getTransType());
            item.put("trans_type_detail", t.getTransTypeDetail());
            item.put("trans_num", t.getTransNum());
            item.put("trans_unit", t.getTransUnit());
            item.put("base_amt", t.getBaseAmt());
            item.put("trans_amt", t.getTransAmt());
            item.put("settle_amt", t.getSettleAmt());
            item.put("balance_amt", t.getBalanceAmt());
            item.put("currency_code", t.getCurrencyCode());
            item.put("ex_code", t.getExCode());
            list.add(item);
        }

        Map<String, Object> body = success();
        int consumed = offset + rows.size();
        if (consumed < total) {
            body.put("next_page", String.valueOf(consumed));
        }
        body.put("trans_cnt", list.size());
        body.put("trans_list", list);
        return body;
    }

    /** 금투-004 계좌 상품정보 조회 (종목코드 오름차순) */
    @PostMapping("/accounts/products")
    public Map<String, Object> products(@RequestAttribute(MyDataHeaderInterceptor.PROFILE_CODE_ATTR) String profileCode,
                                        @RequestBody Map<String, Object> request) {
        MockAccount account = requireAccount(profileCode, request);

        List<MockHolding> holdings = holdingMapper.findByAccountNum(profileCode, account.getAccountNum());
        List<Map<String, Object>> list = new ArrayList<>();
        for (MockHolding h : holdings) {
            BigDecimal purchaseAmt = h.getHoldingNum().multiply(h.getAvgPrice())
                    .setScale(3, RoundingMode.HALF_UP);
            BigDecimal evalAmt = h.getHoldingNum().multiply(h.getCurrentPrice())
                    .setScale(3, RoundingMode.HALF_UP);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("prod_type", h.getProdType());
            item.put("prod_type_detail", "주식");
            item.put("prod_code", h.getProdCode());
            item.put("ex_code", h.getExCode());
            item.put("prod_name", h.getProdName());
            item.put("credit_type", "01");
            item.put("is_tax_benefits", false);
            item.put("purchase_amt", purchaseAmt);
            item.put("holding_num", h.getHoldingNum());
            item.put("trans_unit", "주");
            item.put("eval_amt", evalAmt);
            item.put("is_execution", true);
            item.put("currency_code", "KRW");
            list.add(item);
        }

        Map<String, Object> body = success();
        body.put("search_timestamp", now());
        body.put("base_date", LocalDate.now().format(DATE));
        body.put("prod_cnt", list.size());
        body.put("prod_list", list);
        return body;
    }

    private MockAccount requireAccount(String profileCode, Map<String, Object> request) {
        String accountNum = asString(request.get("account_num"));
        if (accountNum == null) {
            throw MockApiException.badRequest("account_num은 필수입니다.");
        }
        MockAccount account = accountMapper.findByAccountNum(profileCode, accountNum);
        if (account == null) {
            throw MockApiException.notFound("등록되지 않은 계좌번호입니다: " + accountNum);
        }
        return account;
    }

    private Map<String, Object> success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rsp_code", "00000");
        body.put("rsp_msg", "정상 처리되었습니다.");
        return body;
    }

    private String now() {
        return LocalDateTime.now().format(TIMESTAMP);
    }

    private int parseNextPage(String nextPage) {
        if (nextPage == null || nextPage.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(nextPage));
        } catch (NumberFormatException e) {
            throw MockApiException.badRequest("next_page 값이 올바르지 않습니다: " + nextPage);
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
