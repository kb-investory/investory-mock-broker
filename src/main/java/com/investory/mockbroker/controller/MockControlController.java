package com.investory.mockbroker.controller;

import com.investory.mockbroker.domain.MockAccount;
import com.investory.mockbroker.domain.MockHolding;
import com.investory.mockbroker.domain.MockPrice;
import com.investory.mockbroker.domain.MockTransaction;
import com.investory.mockbroker.domain.MockUser;
import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.dto.OrderRequest;
import com.investory.mockbroker.dto.OrderResult;
import com.investory.mockbroker.mapper.AccountMapper;
import com.investory.mockbroker.mapper.HoldingMapper;
import com.investory.mockbroker.mapper.PriceMapper;
import com.investory.mockbroker.mapper.TransactionMapper;
import com.investory.mockbroker.mapper.UserMapper;
import com.investory.mockbroker.seed.ScenarioDefinition;
import com.investory.mockbroker.service.MarketQuoteService;
import com.investory.mockbroker.service.OrderService;
import com.investory.mockbroker.service.ScenarioService;
import com.investory.mockbroker.service.StockMasterService;
import com.investory.mockbroker.web.MyDataHeaderInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 마이데이터 규격 밖의 데모 조작용 API.
 *
 * 실제 증권사에는 없는 엔드포인트이며, 로그인해서 받은 접근토큰이 가리키는 유저 데이터를
 * 직접 조작·조회하는 용도다. 규격 API(/v2/invest/**)와 섞이지 않도록 경로를 /mock 으로
 * 분리했고, 로그인 자체는 /mock/auth/login (AuthController)에서 처리한다.
 */
@RestController
@RequestMapping("/mock")
public class MockControlController {

    private static final String PROFILE_CODE = MyDataHeaderInterceptor.PROFILE_CODE_ATTR;

    private final ScenarioService scenarioService;
    private final OrderService orderService;
    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final PriceMapper priceMapper;
    private final TransactionMapper transactionMapper;
    private final MarketQuoteService marketQuoteService;
    private final StockMasterService stockMasterService;

    @Autowired
    public MockControlController(ScenarioService scenarioService, OrderService orderService,
                                 UserMapper userMapper, AccountMapper accountMapper,
                                 HoldingMapper holdingMapper, PriceMapper priceMapper,
                                 TransactionMapper transactionMapper, MarketQuoteService marketQuoteService,
                                 StockMasterService stockMasterService) {
        this.scenarioService = scenarioService;
        this.orderService = orderService;
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.holdingMapper = holdingMapper;
        this.priceMapper = priceMapper;
        this.transactionMapper = transactionMapper;
        this.marketQuoteService = marketQuoteService;
        this.stockMasterService = stockMasterService;
    }

    /** 코스피 전 종목 코드·이름만 (콘솔 종목 선택창용, 네트워크 호출 없음). */
    @GetMapping("/products")
    public Map<String, Object> products() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ScenarioDefinition.PriceSeed p : stockMasterService.listAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("prodCode", p.getProdCode());
            item.put("prodName", p.getProdName());
            list.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("products", list);
        return body;
    }

    /**
     * 특정 종목의 현재가를 조회한다. 이미 조회·거래된 적 있으면 저장된 값을 그대로,
     * 처음 보는 종목이면 지금 네이버에서 실시간으로 가져와 활성화한다.
     */
    @GetMapping("/prices/{prodCode}")
    public Map<String, Object> priceOf(@RequestAttribute(PROFILE_CODE) String profileCode,
                                       @PathVariable String prodCode) {
        MockPrice price = stockMasterService.ensureActivated(profileCode, prodCode);
        if (price == null) {
            throw MockApiException.notFound("등록되지 않은 종목코드입니다: " + prodCode);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prodCode", price.getProdCode());
        body.put("prodName", price.getProdName());
        body.put("currentPrice", price.getCurrentPrice());
        return body;
    }

    /**
     * 매수·매도 주문. tradedAt을 생략하면 요청 즉시 체결되는 라이브 주문이고,
     * tradedAt(yyyyMMddHHmmss)을 채우면 그 시각에 있었던 것처럼 과거 거래를 소급 등록한다.
     */
    @PostMapping("/orders")
    public OrderResult order(@RequestAttribute(PROFILE_CODE) String profileCode,
                             @RequestBody OrderRequest request) {
        return orderService.execute(profileCode, request.getAccountNum(), request.getProdCode(),
                request.getSide(), request.getQuantity(), request.getPrice(), request.getTradedAt());
    }

    /** 로그인된 유저의 데이터만 초기 시드 상태로 되돌린다. 다른 유저는 영향받지 않는다. */
    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestAttribute(PROFILE_CODE) String profileCode) {
        ScenarioDefinition def = scenarioService.reset(profileCode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orgName", def.getOrgName());
        body.put("message", "초기 상태로 되돌렸습니다.");
        return body;
    }

    /** 콘솔 화면용 통합 상태 조회 */
    @GetMapping("/state")
    public Map<String, Object> state(@RequestAttribute(PROFILE_CODE) String profileCode,
                                     @RequestParam(value = "accountNum", required = false) String accountNum,
                                     @RequestParam(value = "txnLimit", defaultValue = "500") int txnLimit) {
        MockUser user = userMapper.findByProfileCode(profileCode);
        if (user == null) {
            throw MockApiException.notFound("등록되지 않은 연결입니다.");
        }

        List<MockAccount> accounts = accountMapper.findByOrgCode(profileCode, user.getOrgCode());
        if (accounts.isEmpty()) {
            throw MockApiException.notFound("계좌가 없습니다.");
        }
        MockAccount target = accounts.get(0);
        if (accountNum != null && !accountNum.isEmpty()) {
            MockAccount found = accountMapper.findByAccountNum(profileCode, accountNum);
            if (found == null) {
                throw MockApiException.notFound("등록되지 않은 계좌번호입니다: " + accountNum);
            }
            target = found;
        }

        List<Map<String, Object>> accountList = new ArrayList<>();
        for (MockAccount a : accounts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("accountNum", a.getAccountNum());
            item.put("accountName", a.getAccountName());
            item.put("cashBalance", a.getCashBalance());
            accountList.add(item);
        }

        BigDecimal totalEval = BigDecimal.ZERO;
        List<Map<String, Object>> holdings = new ArrayList<>();
        for (MockHolding h : holdingMapper.findByAccountNum(profileCode, target.getAccountNum())) {
            BigDecimal evalAmt = h.getHoldingNum().multiply(h.getCurrentPrice())
                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal purchaseAmt = h.getHoldingNum().multiply(h.getAvgPrice())
                    .setScale(0, RoundingMode.HALF_UP);
            totalEval = totalEval.add(evalAmt);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("prodCode", h.getProdCode());
            item.put("prodName", h.getProdName());
            item.put("holdingNum", h.getHoldingNum().stripTrailingZeros().toPlainString());
            item.put("avgPrice", h.getAvgPrice().setScale(0, RoundingMode.HALF_UP));
            item.put("currentPrice", h.getCurrentPrice().setScale(0, RoundingMode.HALF_UP));
            item.put("purchaseAmt", purchaseAmt);
            item.put("evalAmt", evalAmt);
            item.put("pnl", evalAmt.subtract(purchaseAmt));
            holdings.add(item);
        }

        List<Map<String, Object>> transactions = new ArrayList<>();
        for (MockTransaction t : transactionMapper.findRecent(profileCode, target.getAccountNum(), txnLimit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("transNo", t.getTransNo());
            item.put("transDtime", t.getTransDtime());
            item.put("transType", t.getTransType());
            item.put("transTypeDetail", t.getTransTypeDetail());
            item.put("prodName", t.getProdName());
            item.put("transNum", t.getTransNum().stripTrailingZeros().toPlainString());
            item.put("baseAmt", t.getBaseAmt().setScale(0, RoundingMode.HALF_UP));
            item.put("settleAmt", t.getSettleAmt());
            transactions.add(item);
        }

        List<Map<String, Object>> prices = new ArrayList<>();
        for (MockPrice p : priceMapper.findAll(profileCode)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("prodCode", p.getProdCode());
            item.put("prodName", p.getProdName());
            item.put("currentPrice", p.getCurrentPrice().setScale(0, RoundingMode.HALF_UP));
            prices.add(item);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectionId", user.getConnectionId());
        body.put("orgCode", user.getOrgCode());
        body.put("orgName", user.getOrgName());
        body.put("selectedAccountNum", target.getAccountNum());
        body.put("cashBalance", target.getCashBalance().setScale(0, RoundingMode.HALF_UP));
        body.put("totalEvalAmt", totalEval);
        body.put("accounts", accountList);
        body.put("holdings", holdings);
        body.put("transactions", transactions);
        body.put("prices", prices);
        return body;
    }

    /** 현재가 조정. 평가금액이 움직이는 모습을 보여줄 때 쓴다. */
    @PostMapping("/prices")
    public Map<String, Object> updatePrice(@RequestAttribute(PROFILE_CODE) String profileCode,
                                           @RequestBody Map<String, Object> request) {
        String prodCode = request.get("prodCode") == null ? null : String.valueOf(request.get("prodCode"));
        Object priceValue = request.get("currentPrice");
        if (prodCode == null || priceValue == null) {
            throw MockApiException.badRequest("prodCode와 currentPrice는 필수입니다.");
        }
        MockPrice price = priceMapper.findByProdCode(profileCode, prodCode);
        if (price == null) {
            throw MockApiException.notFound("등록되지 않은 종목코드입니다: " + prodCode);
        }
        BigDecimal newPrice = new BigDecimal(String.valueOf(priceValue));
        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw MockApiException.badRequest("현재가는 0보다 커야 합니다.");
        }
        priceMapper.updateCurrentPrice(profileCode, prodCode, newPrice);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prodCode", prodCode);
        body.put("prodName", price.getProdName());
        body.put("currentPrice", newPrice);
        return body;
    }

    /**
     * 로그인된 유저가 실제로 보유 중인 종목의 현재가만 네이버 금융 시세로 다시 맞춘다.
     * 코스피 전 종목이 시세 목록에 깔려있어 그걸 다 조회하면 너무 느려지므로, 보유 종목으로 좁힌다.
     * 조회에 실패한 종목은 건드리지 않고 그대로 둔다.
     */
    @PostMapping("/prices/refresh")
    public Map<String, Object> refreshPrices(@RequestAttribute(PROFILE_CODE) String profileCode) {
        MockUser user = userMapper.findByProfileCode(profileCode);
        if (user == null) {
            throw MockApiException.notFound("등록되지 않은 연결입니다.");
        }

        Map<String, MockHolding> heldByProdCode = new LinkedHashMap<>();
        for (MockAccount a : accountMapper.findByOrgCode(profileCode, user.getOrgCode())) {
            for (MockHolding h : holdingMapper.findByAccountNum(profileCode, a.getAccountNum())) {
                heldByProdCode.putIfAbsent(h.getProdCode(), h);
            }
        }

        List<Map<String, Object>> updated = new ArrayList<>();
        int failed = 0;
        for (MockHolding h : heldByProdCode.values()) {
            Optional<BigDecimal> quote = marketQuoteService.fetchCurrentPrice(h.getProdCode());
            if (quote.isPresent()) {
                priceMapper.updateCurrentPrice(profileCode, h.getProdCode(), quote.get());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("prodCode", h.getProdCode());
                item.put("prodName", h.getProdName());
                item.put("currentPrice", quote.get());
                updated.add(item);
            } else {
                failed++;
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("updated", updated);
        body.put("updatedCount", updated.size());
        body.put("failedCount", failed);
        return body;
    }
}
