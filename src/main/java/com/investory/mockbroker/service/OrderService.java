package com.investory.mockbroker.service;

import com.investory.mockbroker.domain.MockAccount;
import com.investory.mockbroker.domain.MockHolding;
import com.investory.mockbroker.domain.MockPrice;
import com.investory.mockbroker.domain.MockTransaction;
import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.dto.OrderResult;
import com.investory.mockbroker.mapper.AccountMapper;
import com.investory.mockbroker.mapper.HoldingMapper;
import com.investory.mockbroker.mapper.PriceMapper;
import com.investory.mockbroker.mapper.TransactionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 주문을 받아 즉시 체결시키는 서비스.
 *
 * 실제 증권사의 호가·체결 대기 개념은 재현하지 않는다. 요청이 들어오면 그 자리에서
 * 예수금과 보유수량을 갱신하고 거래내역 한 건을 남긴다.
 */
@Service
public class OrderService {

    /** 위탁수수료율 (데모용 단순화 값) */
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.00015");
    /** 매도 시 제세금율 (데모용 단순화 값) */
    private static final BigDecimal SELL_TAX_RATE = new BigDecimal("0.0015");
    /**
     * 실제 국내 증시의 상한가/하한가 제한폭(±30%)을 흉내낸 값.
     * 실거래는 전일 종가 기준이지만, 여기서는 계산을 단순화해 "현재가" 기준으로 적용한다.
     * 라이브 주문(콘솔에서 지금 넣는 주문)에만 적용하고, 시나리오의 과거 거래 재생에는 적용하지 않는다.
     */
    private static final BigDecimal PRICE_LIMIT_RATE = new BigDecimal("0.30");

    public static final String TRANS_TYPE_BUY = "101";
    public static final String TRANS_TYPE_SELL = "102";

    private static final DateTimeFormatter DTIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final PriceMapper priceMapper;
    private final TransactionMapper transactionMapper;
    private final StockMasterService stockMasterService;

    @Autowired
    public OrderService(AccountMapper accountMapper, HoldingMapper holdingMapper,
                        PriceMapper priceMapper, TransactionMapper transactionMapper,
                        StockMasterService stockMasterService) {
        this.accountMapper = accountMapper;
        this.holdingMapper = holdingMapper;
        this.priceMapper = priceMapper;
        this.transactionMapper = transactionMapper;
        this.stockMasterService = stockMasterService;
    }

    @Transactional
    public OrderResult execute(String profileCode, String accountNum, String prodCode, String side,
                               BigDecimal quantity, BigDecimal requestedPrice, String tradedAt) {

        if (accountNum == null || prodCode == null || side == null) {
            throw MockApiException.badRequest("accountNum, prodCode, side는 필수입니다.");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw MockApiException.badRequest("quantity는 0보다 커야 합니다.");
        }
        if (tradedAt != null && !tradedAt.isEmpty() && !tradedAt.matches("\\d{14}")) {
            throw MockApiException.badRequest("tradedAt은 yyyyMMddHHmmss(14자리 숫자) 형식이어야 합니다: " + tradedAt);
        }

        boolean buy;
        if ("BUY".equalsIgnoreCase(side)) {
            buy = true;
        } else if ("SELL".equalsIgnoreCase(side)) {
            buy = false;
        } else {
            throw MockApiException.badRequest("side는 BUY 또는 SELL이어야 합니다. 입력값: " + side);
        }

        MockAccount account = accountMapper.findByAccountNum(profileCode, accountNum);
        if (account == null) {
            throw MockApiException.notFound("등록되지 않은 계좌번호입니다: " + accountNum);
        }
        // 아직 이 유저 앞으로 조회·거래된 적 없는 종목이면 코스피 마스터 목록을 보고 지금 활성화한다.
        MockPrice price = stockMasterService.ensureActivated(profileCode, prodCode);
        if (price == null) {
            throw MockApiException.notFound("등록되지 않은 종목코드입니다: " + prodCode);
        }

        // tradedAt이 있으면 시나리오의 과거 거래 재생, 없으면 콘솔에서 지금 넣은 라이브 주문이다.
        boolean liveOrder = (tradedAt == null || tradedAt.isEmpty());

        BigDecimal unitPrice = requestedPrice != null ? requestedPrice : price.getCurrentPrice();
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw MockApiException.badRequest("체결단가는 0보다 커야 합니다.");
        }

        if (liveOrder && requestedPrice != null) {
            BigDecimal basis = price.getCurrentPrice();
            BigDecimal upperLimit = basis.multiply(BigDecimal.ONE.add(PRICE_LIMIT_RATE));
            BigDecimal lowerLimit = basis.multiply(BigDecimal.ONE.subtract(PRICE_LIMIT_RATE));
            if (unitPrice.compareTo(upperLimit) > 0 || unitPrice.compareTo(lowerLimit) < 0) {
                throw MockApiException.priceLimitExceeded(String.format(
                        "가격제한폭을 벗어났습니다. 현재가 %s원 기준 허용범위 %s원 ~ %s원, 요청가 %s원",
                        basis.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                        lowerLimit.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                        upperLimit.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                        unitPrice.setScale(0, RoundingMode.HALF_UP).toPlainString()));
            }
        }

        BigDecimal transAmt = unitPrice.multiply(quantity).setScale(3, RoundingMode.HALF_UP);
        BigDecimal commission = transAmt.multiply(COMMISSION_RATE).setScale(0, RoundingMode.DOWN);
        BigDecimal tax = buy ? BigDecimal.ZERO
                : transAmt.multiply(SELL_TAX_RATE).setScale(0, RoundingMode.DOWN);

        MockHolding holding = holdingMapper.findOne(profileCode, accountNum, prodCode);
        BigDecimal settleAmt;
        BigDecimal newCash;
        BigDecimal resultHoldingNum;
        BigDecimal resultAvgPrice;

        if (buy) {
            settleAmt = transAmt.add(commission);
            if (account.getCashBalance().compareTo(settleAmt) < 0) {
                throw MockApiException.insufficientCash(String.format(
                        "예수금이 부족합니다. 필요 %s원, 보유 %s원",
                        settleAmt.toPlainString(), account.getCashBalance().toPlainString()));
            }
            newCash = account.getCashBalance().subtract(settleAmt);

            if (holding == null) {
                resultHoldingNum = quantity;
                resultAvgPrice = unitPrice;
                MockHolding created = new MockHolding();
                created.setProfileCode(profileCode);
                created.setAccountNum(accountNum);
                created.setProdCode(prodCode);
                created.setHoldingNum(resultHoldingNum);
                created.setAvgPrice(resultAvgPrice);
                holdingMapper.insert(created);
            } else {
                BigDecimal beforeCost = holding.getHoldingNum().multiply(holding.getAvgPrice());
                BigDecimal addedCost = quantity.multiply(unitPrice);
                resultHoldingNum = holding.getHoldingNum().add(quantity);
                resultAvgPrice = beforeCost.add(addedCost)
                        .divide(resultHoldingNum, 8, RoundingMode.HALF_UP);
                holding.setHoldingNum(resultHoldingNum);
                holding.setAvgPrice(resultAvgPrice);
                holdingMapper.update(holding);
            }
        } else {
            if (holding == null || holding.getHoldingNum().compareTo(quantity) < 0) {
                BigDecimal owned = holding == null ? BigDecimal.ZERO : holding.getHoldingNum();
                throw MockApiException.insufficientHolding(String.format(
                        "보유수량이 부족합니다. 매도요청 %s주, 보유 %s주",
                        quantity.toPlainString(), owned.toPlainString()));
            }
            settleAmt = transAmt.subtract(commission).subtract(tax);
            newCash = account.getCashBalance().add(settleAmt);

            resultHoldingNum = holding.getHoldingNum().subtract(quantity);
            resultAvgPrice = holding.getAvgPrice();
            if (resultHoldingNum.compareTo(BigDecimal.ZERO) == 0) {
                holdingMapper.delete(profileCode, accountNum, prodCode);
            } else {
                holding.setHoldingNum(resultHoldingNum);
                holdingMapper.update(holding);
            }
        }

        newCash = newCash.setScale(3, RoundingMode.HALF_UP);
        accountMapper.updateCashBalance(profileCode, accountNum, newCash);

        String transDtime = liveOrder ? LocalDateTime.now().format(DTIME) : tradedAt;
        String transNo = nextTransNo(profileCode, accountNum, transDtime.substring(0, 8));

        MockTransaction txn = new MockTransaction();
        txn.setProfileCode(profileCode);
        txn.setAccountNum(accountNum);
        txn.setTransNo(transNo);
        txn.setTransDtime(transDtime);
        txn.setTransType(buy ? TRANS_TYPE_BUY : TRANS_TYPE_SELL);
        txn.setTransTypeDetail(buy ? "주식 매수" : "주식 매도");
        txn.setProdCode(prodCode);
        txn.setProdName(price.getProdName());
        txn.setTransNum(quantity);
        txn.setTransUnit("주");
        txn.setBaseAmt(unitPrice);
        txn.setTransAmt(transAmt);
        txn.setSettleAmt(settleAmt);
        txn.setBalanceAmt(newCash);
        txn.setCurrencyCode("KRW");
        txn.setExCode(price.getExCode());
        transactionMapper.insert(txn);

        // 실제 시장처럼, 방금 체결된 가격이 다음 주문의 기준이 되는 새로운 현재가가 된다.
        // 시나리오 재생 중에는 시드 파일이 지정한 "오늘의 현재가"를 그대로 유지해야 하므로 건드리지 않는다.
        if (liveOrder) {
            priceMapper.updateCurrentPrice(profileCode, prodCode, unitPrice);
        }

        OrderResult result = new OrderResult();
        result.setAccountNum(accountNum);
        result.setTransNo(transNo);
        result.setTransDtime(transDtime);
        result.setSide(buy ? "BUY" : "SELL");
        result.setProdCode(prodCode);
        result.setProdName(price.getProdName());
        result.setQuantity(quantity);
        result.setUnitPrice(unitPrice);
        result.setTransAmt(transAmt);
        result.setCommission(commission);
        result.setTax(tax);
        result.setSettleAmt(settleAmt);
        result.setCashBalance(newCash);
        result.setHoldingNum(resultHoldingNum);
        result.setAvgPrice(resultAvgPrice);
        return result;
    }

    /**
     * 거래번호는 계좌·일자별 일련번호로 만든다.
     * Investory 쪽에서 external_trade_id 로 그대로 사용하면 재동기화해도 중복 적재되지 않는다.
     */
    private String nextTransNo(String profileCode, String accountNum, String dateStr) {
        int count = transactionMapper.countByAccountAndDate(profileCode, accountNum, dateStr);
        return dateStr + "-" + String.format("%04d", count + 1);
    }
}
