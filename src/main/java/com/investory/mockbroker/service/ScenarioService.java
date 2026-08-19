package com.investory.mockbroker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investory.mockbroker.domain.MockAccount;
import com.investory.mockbroker.domain.MockOrg;
import com.investory.mockbroker.domain.MockPrice;
import com.investory.mockbroker.domain.MockUser;
import com.investory.mockbroker.domain.Security;
import com.investory.mockbroker.dto.GenerateScenarioRequest;
import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.domain.MockScenarioTemplate;
import com.investory.mockbroker.mapper.AccountMapper;
import com.investory.mockbroker.mapper.HoldingMapper;
import com.investory.mockbroker.mapper.OrgMapper;
import com.investory.mockbroker.mapper.PriceMapper;
import com.investory.mockbroker.mapper.SecurityMapper;
import com.investory.mockbroker.mapper.TemplateMapper;
import com.investory.mockbroker.mapper.TransactionMapper;
import com.investory.mockbroker.mapper.UserMapper;
import com.investory.mockbroker.seed.ScenarioDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * resources/scenarios/*.json에 번들된 "거래 패턴" 템플릿을 관리하고, 관리자 콘솔이 특정 유저에게
 * 그 템플릿(또는 직접 입력한 JSON)을 적용해 계좌·시세·거래이력을 만들어내는 서비스.
 *
 * 템플릿 파일에는 로그인 자격증명이 없다 — 유저는 항상 미리 존재해야 하고(예: /mock/auth/register),
 * 관리자가 그 유저를 골라 템플릿을 적용하는 방식이다. 부팅 시 자동으로 유저를 만들지 않는다.
 */
@Service
public class ScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioService.class);
    private static final String SCENARIO_PATTERN = "classpath*:scenarios/*.json";

    /** 회원가입으로 만들어지는 유저에게 붙는 기본 증권사(가상)와 계좌. 시드 JSON이 없어도 바로 거래를 시작할 수 있게 한다. */
    private static final String SIGNUP_ORG_CODE = "S9990099A";
    private static final String SIGNUP_ORG_NAME = "테스트증권(모의)";
    private static final String SIGNUP_ACCOUNT_NUM = "9000000001";
    private static final BigDecimal SIGNUP_INITIAL_CASH = new BigDecimal("10000000");
    /** {종목코드, 종목명, 시장구분, 네이버 시세 조회 실패 시 쓸 폴백 가격} */
    private static final String[][] SIGNUP_PRICES = {
        {"005930", "삼성전자", "KOSPI", "84200"},
        {"000660", "SK하이닉스", "KOSPI", "191500"},
        {"035420", "NAVER", "KOSPI", "218000"},
        {"373220", "LG에너지솔루션", "KOSPI", "385000"},
        {"042700", "한미반도체", "KOSPI", "96500"},
    };

    /** 영업일 하루마다 매수·매도턴이 뜰 기본 확률. */
    private static final double DEFAULT_BUY_PROBABILITY = 0.35;
    private static final double DEFAULT_SELL_PROBABILITY = 0.15;
    /** 매수·매도턴이 뜨면, 그 후보(살 수 있는/팔 수 있는) 종목마다 독립적으로 굴려서 포함
     *  여부를 정하는 확률 — 개수 상한을 두지 않고 후보 규모에 자연스럽게 비례하게 한다.
     *  매도는 "팔 대상이 있으면 실제로 잘 팔리게" 매수보다 높게 잡았다. */
    private static final double BUY_PICK_PROBABILITY = 0.15;
    private static final double SELL_PICK_PROBABILITY = 0.3;
    /** 매수 한 건에 쓸 현금 비율(남은 예수금 대비). */
    private static final double MIN_BUY_CASH_PORTION = 0.05;
    private static final double MAX_BUY_CASH_PORTION = 0.20;
    /** 매도 한 건에 팔 보유수량 비율. */
    private static final double MIN_SELL_QTY_PORTION = 0.2;
    private static final double MAX_SELL_QTY_PORTION = 1.0;
    /** OrderService의 실제 수수료율(COMMISSION_RATE)·매도 제세금율(SELL_TAX_RATE)에 맞춘 근사값 —
     *  생성 시점 시뮬레이션이 실제 체결 시 소진되는 금액을 과소추정해 예수금 부족이 나지 않게 한다. */
    private static final BigDecimal BUY_COMMISSION_RATE = new BigDecimal("0.00015");
    private static final BigDecimal SELL_FEE_RATE = new BigDecimal("0.00165");

    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final PriceMapper priceMapper;
    private final HoldingMapper holdingMapper;
    private final TransactionMapper transactionMapper;
    private final TemplateMapper templateMapper;
    private final OrderService orderService;
    private final MarketQuoteService marketQuoteService;
    private final ClientService clientService;
    private final OrgMapper orgMapper;
    private final StockMasterService stockMasterService;
    private final SecurityMapper securityMapper;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final Map<String, ScenarioDefinition> scenarios = new LinkedHashMap<>();

    @Autowired
    public ScenarioService(UserMapper userMapper, AccountMapper accountMapper,
                           PriceMapper priceMapper, HoldingMapper holdingMapper,
                           TransactionMapper transactionMapper, TemplateMapper templateMapper,
                           OrderService orderService, MarketQuoteService marketQuoteService,
                           ClientService clientService, OrgMapper orgMapper,
                           StockMasterService stockMasterService, SecurityMapper securityMapper,
                           PlatformTransactionManager transactionManager) {
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.priceMapper = priceMapper;
        this.holdingMapper = holdingMapper;
        this.transactionMapper = transactionMapper;
        this.templateMapper = templateMapper;
        this.orderService = orderService;
        this.marketQuoteService = marketQuoteService;
        this.clientService = clientService;
        this.orgMapper = orgMapper;
        this.securityMapper = securityMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.stockMasterService = stockMasterService;
    }

    @PostConstruct
    public void init() {
        clientService.ensureDefaultClient();
        loadScenarioFiles();
    }

    private void loadScenarioFiles() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(SCENARIO_PATTERN);
            List<Resource> sorted = new ArrayList<>();
            for (Resource r : resources) {
                sorted.add(r);
            }
            sorted.sort(Comparator.comparing(r -> String.valueOf(r.getFilename())));

            for (Resource resource : sorted) {
                try (InputStream in = resource.getInputStream()) {
                    ScenarioDefinition def = objectMapper.readValue(in, ScenarioDefinition.class);
                    if (def.getTemplateId() == null || def.getTemplateId().isEmpty()) {
                        log.warn("templateId가 없어 건너뜁니다: {}", resource.getFilename());
                        continue;
                    }
                    scenarios.put(def.getTemplateId(), def);
                    log.info("시나리오 템플릿 적재: {} ({})", def.getTemplateId(), resource.getFilename());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("시드 파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }

    /** 관리자 콘솔의 템플릿 선택창용 — 번들된 템플릿 전체를 로드된 순서 그대로 돌려준다. */
    public List<ScenarioDefinition> listTemplates() {
        return new ArrayList<>(scenarios.values());
    }

    /** 번들 파일 템플릿을 먼저 찾고, 없으면 관제 콘솔에서 저장한 DB 커스텀 템플릿을 찾는다. */
    public ScenarioDefinition getTemplate(String templateId) {
        ScenarioDefinition fileDef = scenarios.get(templateId);
        if (fileDef != null) {
            return fileDef;
        }
        MockScenarioTemplate dbTemplate = templateMapper.findById(templateId);
        if (dbTemplate == null) {
            throw MockApiException.notFound("등록되지 않은 템플릿입니다: " + templateId);
        }
        return parseDefinition(dbTemplate.getDefinitionJson());
    }

    /** 관제 콘솔의 템플릿 선택창용 — 번들 파일 템플릿 + DB 커스텀 템플릿을 합쳐 돌려준다. */
    public List<TemplateSummary> listAllTemplates() {
        List<TemplateSummary> result = new ArrayList<>();
        for (ScenarioDefinition def : scenarios.values()) {
            result.add(new TemplateSummary(def.getTemplateId(), def.getProfileName(), def.getDescription(), "FILE"));
        }
        for (MockScenarioTemplate t : templateMapper.findAll()) {
            result.add(new TemplateSummary(t.getTemplateId(), t.getProfileName(), t.getDescription(), "DB"));
        }
        return result;
    }

    /**
     * 관제 콘솔에서 직접 입력한(또는 적용한) 시나리오 JSON을 재사용 가능한 커스텀 템플릿으로
     * DB에 저장한다. templateId가 번들 파일 템플릿과 겹치면 거부한다 — 기본 제공 템플릿을
     * 실수로 덮어쓰는 걸 막기 위함이다.
     */
    public synchronized void saveCustomTemplate(ScenarioDefinition def) {
        if (def.getTemplateId() == null || def.getTemplateId().isEmpty()) {
            throw MockApiException.badRequest("templateId는 필수입니다.");
        }
        if (scenarios.containsKey(def.getTemplateId())) {
            throw MockApiException.badRequest("번들 템플릿과 같은 templateId는 쓸 수 없습니다: " + def.getTemplateId());
        }
        MockScenarioTemplate row = new MockScenarioTemplate();
        row.setTemplateId(def.getTemplateId());
        row.setProfileName(def.getProfileName());
        row.setDescription(def.getDescription());
        row.setDefinitionJson(writeDefinition(def));
        templateMapper.upsert(row);
    }

    /** DB 커스텀 템플릿만 삭제 가능하다 — 번들 파일 템플릿 ID면 거부한다. */
    public synchronized void deleteCustomTemplate(String templateId) {
        if (scenarios.containsKey(templateId)) {
            throw MockApiException.badRequest("번들 템플릿은 삭제할 수 없습니다: " + templateId);
        }
        int affected = templateMapper.delete(templateId);
        if (affected == 0) {
            throw MockApiException.notFound("등록되지 않은 템플릿입니다: " + templateId);
        }
    }

    /**
     * 실제 과거 시세(네이버 일별시세)를 시나리오 시작일부터 하루씩 재생하며, 매수턴·매도턴을
     * 확률적으로 섞어 거래를 채운 시나리오 템플릿을 만들어 DB에 저장한다. 본 서비스 분석
     * 기능이 요구하는 "최소 N일치 거래이력" 테스트 데이터를 손으로 가격을 지어내지 않고
     * 준비하기 위함이다 — 계좌 개설일도 그 기간 시작일로 맞춘다.
     *
     * 하루마다: 휴장일(그 종목이 그날 거래된 기록이 없음)이면 건너뛰고, 아니면 매수턴·매도턴이
     * 각각 뜰지 독립적으로 확률을 굴린다. 뜬 턴이 있으면 그날 종가를 매수·매도 가격으로 그대로
     * 쓴다(단순화를 위해 저가~고가 사이 무작위 대신 종가로 통일) — 매수턴은 그 가격으로 살 수
     * 있는 종목 중 일부를 먼저 사고, 매도턴은 그 다음 보유 종목 중 일부를 판다. 예수금·보유수량은
     * 이 메서드 안에서 직접 추적해서, 나중에 실제 유저에게 적용할 때 OrderService가 예수금/보유
     * 수량 부족으로 거부하는 일이 없게 한다.
     */
    public synchronized ScenarioDefinition generateHistoricalTemplate(GenerateScenarioRequest req) {
        if (req.getTemplateId() == null || req.getTemplateId().isEmpty()) {
            throw MockApiException.badRequest("templateId는 필수입니다.");
        }
        if (req.getOrgCode() == null || req.getOrgCode().isEmpty()
                || req.getOrgName() == null || req.getOrgName().isEmpty()) {
            throw MockApiException.badRequest("orgCode/orgName은 필수입니다.");
        }
        if (req.getAccountNum() == null || req.getAccountNum().isEmpty()) {
            throw MockApiException.badRequest("accountNum은 필수입니다.");
        }

        BigDecimal initialCash = req.getInitialCash() != null ? req.getInitialCash() : new BigDecimal("10000000");
        int days = req.getDays() != null ? req.getDays() : 90;
        double buyProbability = req.getBuyProbability() != null ? req.getBuyProbability() : DEFAULT_BUY_PROBABILITY;
        double sellProbability = req.getSellProbability() != null ? req.getSellProbability() : DEFAULT_SELL_PROBABILITY;
        if (days < 1) {
            throw MockApiException.badRequest("days는 1 이상이어야 합니다.");
        }
        if (buyProbability < 0 || buyProbability > 1 || sellProbability < 0 || sellProbability > 1) {
            throw MockApiException.badRequest("buyProbability/sellProbability는 0~1 사이여야 합니다.");
        }

        // prodCodes를 안 넘기면 DB securities 테이블의 활성 종목(코스피·코스닥) 전체를 기본
        // 바스켓으로 쓴다 — 메인 서비스와 통일해서 쓰는 실제 종목 마스터라 이 목업 서버가 별도로
        // 순위를 매기거나 목록을 들고 있을 필요가 없다.
        List<String> prodCodes;
        Map<String, String[]> securityNames = new LinkedHashMap<>();
        if (req.getProdCodes() != null && !req.getProdCodes().isEmpty()) {
            prodCodes = req.getProdCodes();
        } else {
            prodCodes = new ArrayList<>();
            for (Security s : securityMapper.findAllActive()) {
                prodCodes.add(s.getSecurityCode());
                securityNames.put(s.getSecurityCode(), new String[]{s.getSecurityName(), s.getMarketType()});
            }
            if (prodCodes.isEmpty()) {
                throw MockApiException.badRequest(
                        "prodCodes가 없고 securities 테이블에 활성 종목도 없습니다. prodCodes를 직접 지정하세요.");
            }
        }

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);

        // 종목별 과거 일별 시세를 통째로 한 번씩만 가져와 날짜별로 인덱싱해둔다 — 아래 날짜 루프는
        // 전부 메모리 안에서 돌고 추가 네트워크 호출은 없다. 바스켓이 클 수 있어(전체 활성 종목),
        // 한두 종목의 시세 조회가 실패해도 전체를 막지 않고 그 종목만 건너뛴다.
        List<ScenarioDefinition.PriceSeed> prices = new ArrayList<>();
        Map<String, Map<String, MarketQuoteService.HistoricalPrice>> historyByCode = new LinkedHashMap<>();
        Set<String> tradingDates = new TreeSet<>();
        List<String> resolvedProdCodes = new ArrayList<>();

        for (String prodCode : prodCodes) {
            List<MarketQuoteService.HistoricalPrice> history =
                    marketQuoteService.fetchHistoricalPrices(prodCode, from, to);
            if (history.isEmpty()) {
                log.warn("과거 시세를 가져오지 못해 건너뜁니다: {}", prodCode);
                continue;
            }
            resolvedProdCodes.add(prodCode);
            Map<String, MarketQuoteService.HistoricalPrice> byDate = new LinkedHashMap<>();
            for (MarketQuoteService.HistoricalPrice h : history) {
                byDate.put(h.getDate(), h);
                tradingDates.add(h.getDate());
            }
            historyByCode.put(prodCode, byDate);

            String[] known = securityNames.get(prodCode);
            ScenarioDefinition.PriceSeed master = stockMasterService.findByCode(prodCode);
            ScenarioDefinition.PriceSeed price = new ScenarioDefinition.PriceSeed();
            price.setProdCode(prodCode);
            price.setProdName(known != null ? known[0] : master != null ? master.getProdName() : prodCode);
            price.setMarketType(known != null ? known[1] : master != null ? master.getMarketType() : "KOSPI");
            if (master != null) {
                price.setProdType(master.getProdType());
                price.setExCode(master.getExCode());
            }
            price.setCurrentPrice(history.get(history.size() - 1).getClosePrice());
            prices.add(price);
        }
        if (prices.isEmpty()) {
            throw MockApiException.badRequest("지정한 종목 전부 과거 시세를 가져오지 못했습니다.");
        }
        prodCodes = resolvedProdCodes;

        Random random = new Random();
        BigDecimal cash = initialCash;
        Map<String, BigDecimal> holdings = new LinkedHashMap<>();
        List<ScenarioDefinition.TradeSeed> trades = new ArrayList<>();

        for (String date : tradingDates) {
            boolean buyTurn = random.nextDouble() < buyProbability;
            boolean sellTurn = random.nextDouble() < sellProbability;
            if (!buyTurn && !sellTurn) {
                continue;
            }

            // 매수·매도 둘 다 같은 "오늘의 가격"을 쓴다 — 단순화를 위해 그날 종가로 통일한다.
            Map<String, BigDecimal> todayPrice = new LinkedHashMap<>();
            for (String prodCode : prodCodes) {
                MarketQuoteService.HistoricalPrice h = historyByCode.get(prodCode).get(date);
                if (h == null) {
                    continue; // 이 종목은 오늘 거래가 없었다(상장 전 등)
                }
                todayPrice.put(prodCode, h.getClosePrice());
            }

            if (buyTurn) {
                List<String> affordable = new ArrayList<>();
                for (Map.Entry<String, BigDecimal> e : todayPrice.entrySet()) {
                    if (e.getValue().compareTo(cash) <= 0) {
                        affordable.add(e.getKey());
                    }
                }
                Collections.shuffle(affordable, random);
                for (String prodCode : affordable) {
                    if (random.nextDouble() >= BUY_PICK_PROBABILITY) {
                        continue;
                    }
                    BigDecimal price = todayPrice.get(prodCode);
                    double portion = MIN_BUY_CASH_PORTION
                            + random.nextDouble() * (MAX_BUY_CASH_PORTION - MIN_BUY_CASH_PORTION);
                    BigDecimal qty = cash.multiply(BigDecimal.valueOf(portion)).divide(price, 0, RoundingMode.DOWN);
                    if (qty.compareTo(BigDecimal.ONE) < 0) {
                        continue;
                    }
                    BigDecimal cost = qty.multiply(price).multiply(BigDecimal.ONE.add(BUY_COMMISSION_RATE));
                    if (cost.compareTo(cash) > 0) {
                        continue;
                    }
                    trades.add(tradeSeed(req.getAccountNum(), date, randomTime(9, 11, random),
                            "BUY", prodCode, qty, price));
                    cash = cash.subtract(cost);
                    holdings.merge(prodCode, qty, BigDecimal::add);
                }
            }

            if (sellTurn) {
                List<String> sellable = new ArrayList<>();
                for (Map.Entry<String, BigDecimal> e : holdings.entrySet()) {
                    if (e.getValue().compareTo(BigDecimal.ZERO) > 0 && todayPrice.containsKey(e.getKey())) {
                        sellable.add(e.getKey());
                    }
                }
                Collections.shuffle(sellable, random);
                for (String prodCode : sellable) {
                    if (random.nextDouble() >= SELL_PICK_PROBABILITY) {
                        continue;
                    }
                    BigDecimal held = holdings.get(prodCode);
                    double portion = MIN_SELL_QTY_PORTION
                            + random.nextDouble() * (MAX_SELL_QTY_PORTION - MIN_SELL_QTY_PORTION);
                    BigDecimal qty = held.multiply(BigDecimal.valueOf(portion)).setScale(0, RoundingMode.DOWN);
                    if (qty.compareTo(BigDecimal.ONE) < 0) {
                        qty = BigDecimal.ONE;
                    }
                    if (qty.compareTo(held) > 0) {
                        qty = held;
                    }
                    BigDecimal price = todayPrice.get(prodCode);
                    trades.add(tradeSeed(req.getAccountNum(), date, randomTime(12, 15, random),
                            "SELL", prodCode, qty, price));
                    holdings.put(prodCode, held.subtract(qty));
                    cash = cash.add(qty.multiply(price).multiply(BigDecimal.ONE.subtract(SELL_FEE_RATE)));
                }
            }
        }

        ScenarioDefinition.AccountSeed account = new ScenarioDefinition.AccountSeed();
        account.setAccountNum(req.getAccountNum());
        account.setAccountName(req.getAccountName());
        account.setAccountType(req.getAccountType());
        account.setIssueDate(from.format(DateTimeFormatter.BASIC_ISO_DATE));
        account.setInitialCash(initialCash);

        ScenarioDefinition def = new ScenarioDefinition();
        def.setTemplateId(req.getTemplateId());
        def.setProfileName(req.getProfileName() != null && !req.getProfileName().isEmpty()
                ? req.getProfileName() : req.getTemplateId());
        def.setDescription(req.getDescription() != null ? req.getDescription()
                : days + "일치 실제 시세를 하루씩 재생해 자동 생성된 시나리오");
        def.setOrgCode(req.getOrgCode());
        def.setOrgName(req.getOrgName());
        def.setPrices(prices);
        def.setAccounts(new ArrayList<>(List.of(account)));
        def.setTrades(trades);

        saveCustomTemplate(def);
        long sellCount = trades.stream().filter(t -> "SELL".equals(t.getSide())).count();
        log.info("과거 시세 기반 시나리오 생성 완료: {} - 종목 {}개, 거래 {}건(매수 {}/매도 {}), {}일",
                req.getTemplateId(), prodCodes.size(), trades.size(), trades.size() - sellCount, sellCount, days);
        return def;
    }

    private ScenarioDefinition.TradeSeed tradeSeed(String accountNum, String date, String time, String side,
                                                    String prodCode, BigDecimal quantity, BigDecimal price) {
        ScenarioDefinition.TradeSeed trade = new ScenarioDefinition.TradeSeed();
        trade.setAccountNum(accountNum);
        trade.setTradedAt(date + time);
        trade.setSide(side);
        trade.setProdCode(prodCode);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        return trade;
    }

    /**
     * {fromHour}~{toHour} 사이 무작위 시각을 "HHmmss"로 뽑는다. 매수는 9~11시, 매도는 12~15시
     * 대역을 써서 같은 날 매수가 항상 매도보다 먼저 재생되게 한다(시나리오 요구사항: 매수턴 →
     * 매도턴 순서).
     */
    private String randomTime(int fromHour, int toHour, Random random) {
        int hour = fromHour + random.nextInt(Math.max(1, toHour - fromHour));
        int minute = random.nextInt(60);
        int second = random.nextInt(60);
        return String.format("%02d%02d%02d", hour, minute, second);
    }

    private ScenarioDefinition parseDefinition(String json) {
        try {
            return objectMapper.readValue(json, ScenarioDefinition.class);
        } catch (Exception e) {
            throw new IllegalStateException("저장된 템플릿을 읽지 못했습니다.", e);
        }
    }

    /**
     * 유저에게 마지막으로 적용된 시나리오의 templateId만 뽑아낸다 (관제 콘솔의 유저 관리 테이블에서
     * 재적용 select를 그 값으로 미리 선택해두는 용도). 아직 아무 시나리오도 적용 안 됐거나,
     * 직접 입력한 JSON에 templateId가 없었으면 null.
     */
    public String appliedTemplateIdOf(MockUser user) {
        if (user.getScenarioJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(user.getScenarioJson(), ScenarioDefinition.class).getTemplateId();
        } catch (Exception e) {
            return null;
        }
    }

    private String writeDefinition(ScenarioDefinition def) {
        try {
            return objectMapper.writeValueAsString(def);
        } catch (Exception e) {
            throw new IllegalStateException("템플릿을 직렬화하지 못했습니다.", e);
        }
    }

    /** 관제 콘솔 템플릿 선택창 응답용 — 파일/DB 어느 쪽 출처인지(source)를 같이 내려준다. */
    public static class TemplateSummary {
        private final String templateId;
        private final String profileName;
        private final String description;
        private final String source;

        public TemplateSummary(String templateId, String profileName, String description, String source) {
            this.templateId = templateId;
            this.profileName = profileName;
            this.description = description;
            this.source = source;
        }

        public String getTemplateId() { return templateId; }
        public String getProfileName() { return profileName; }
        public String getDescription() { return description; }
        public String getSource() { return source; }
    }

    /**
     * 이미 존재하는 유저(loginId)에게 템플릿(또는 직접 입력한 JSON)을 적용한다. 기존 계좌·시세·
     * 거래이력은 전부 지우고 이 정의로 새로 만든다. 나중에 /mock/reset이 다시 쓸 수 있도록
     * 적용한 JSON을 그 유저 행에 같이 저장해둔다.
     */
    public synchronized MockUser applyScenario(String loginId, ScenarioDefinition def) {
        MockUser user = userMapper.findByLoginId(loginId);
        if (user == null) {
            throw MockApiException.notFound("등록되지 않은 유저입니다: " + loginId);
        }
        String profileCode = user.getProfileCode();
        ensureOrg(def.getOrgCode(), def.getOrgName());
        transactionMapper.deleteByProfileCode(profileCode);
        holdingMapper.deleteByProfileCode(profileCode);
        accountMapper.deleteByProfileCode(profileCode);
        priceMapper.deleteByProfileCode(profileCode);
        seed(def, profileCode);

        String scenarioJson;
        try {
            scenarioJson = objectMapper.writeValueAsString(def);
        } catch (Exception e) {
            throw new IllegalStateException("시나리오를 직렬화하지 못했습니다.", e);
        }
        userMapper.applyScenario(profileCode, def.getOrgCode(), def.getOrgName(), scenarioJson);
        user.setOrgCode(def.getOrgCode());
        user.setOrgName(def.getOrgName());
        user.setScenarioJson(scenarioJson);

        log.info("시나리오 적용 완료: {} - 계좌 {}건, 거래 {}건",
                loginId, def.getAccounts().size(), def.getTrades().size());
        return user;
    }

    /** 이 유저의 데이터만, 마지막으로 적용된 시나리오 기준으로 초기 상태로 되돌린다. */
    public synchronized ScenarioDefinition reset(String profileCode) {
        MockUser user = userMapper.findByProfileCode(profileCode);
        if (user == null || user.getScenarioJson() == null) {
            throw MockApiException.badRequest("이 유저에게는 아직 적용된 시나리오가 없습니다. 관리자 콘솔에서 먼저 시나리오를 적용하세요.");
        }
        ScenarioDefinition def;
        try {
            def = objectMapper.readValue(user.getScenarioJson(), ScenarioDefinition.class);
        } catch (Exception e) {
            throw new IllegalStateException("저장된 시나리오를 읽지 못했습니다.", e);
        }
        transactionMapper.deleteByProfileCode(profileCode);
        holdingMapper.deleteByProfileCode(profileCode);
        accountMapper.deleteByProfileCode(profileCode);
        priceMapper.deleteByProfileCode(profileCode);
        seed(def, profileCode);
        userMapper.applyScenario(profileCode, def.getOrgCode(), def.getOrgName(), user.getScenarioJson());
        log.info("유저 초기화 완료: {} - 계좌 {}건, 거래 {}건",
                user.getLoginId(), def.getAccounts().size(), def.getTrades().size());
        return def;
    }

    /**
     * 시드 JSON 없이, API로 직접 회원가입한 유저를 만든다. 기본 증권사·계좌 1개·기본 종목
     * 시세를 세팅해줘서 가입 직후 바로 /mock/orders로 거래를 시작할 수 있게 한다.
     */
    public synchronized MockUser register(String loginId, String rawPassword) {
        if (userMapper.findByLoginId(loginId) != null) {
            throw MockApiException.badRequest("이미 사용 중인 아이디입니다: " + loginId);
        }
        String profileCode = "USR_" + loginId;
        ensureOrg(SIGNUP_ORG_CODE, SIGNUP_ORG_NAME);

        MockUser user = new MockUser();
        user.setProfileCode(profileCode);
        user.setLoginId(loginId);
        user.setLoginPasswordHash(passwordEncoder.encode(rawPassword));
        user.setConnectionId(UUID.randomUUID().toString().replace("-", ""));
        user.setOrgCode(SIGNUP_ORG_CODE);
        user.setOrgName(SIGNUP_ORG_NAME);
        userMapper.insert(user);

        for (String[] p : SIGNUP_PRICES) {
            MockPrice price = new MockPrice();
            price.setProfileCode(profileCode);
            price.setProdCode(p[0]);
            price.setProdName(p[1]);
            price.setProdType("401");
            price.setExCode("FRK");
            price.setMarketType(p[2]);
            price.setCurrentPrice(marketQuoteService.fetchCurrentPrice(p[0])
                    .orElse(new BigDecimal(p[3])));
            priceMapper.insert(price);
        }

        MockAccount account = new MockAccount();
        account.setProfileCode(profileCode);
        account.setOrgCode(SIGNUP_ORG_CODE);
        account.setAccountNum(SIGNUP_ACCOUNT_NUM);
        account.setAccountName("종합위탁계좌");
        account.setAccountType("101");
        account.setIssueDate(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        account.setCashBalance(SIGNUP_INITIAL_CASH);
        accountMapper.insert(account);

        log.info("회원가입 완료: {}", loginId);
        return user;
    }

    /** orgCode가 mock_org에 없으면 그 자리에서 등록한다 — 백엔드가 동기화하는 목록이 항상 실제 유저 데이터와 맞도록. */
    private void ensureOrg(String orgCode, String orgName) {
        if (orgMapper.findByOrgCode(orgCode) == null) {
            MockOrg org = new MockOrg();
            org.setOrgCode(orgCode);
            org.setOrgName(orgName);
            orgMapper.insert(org);
            log.info("증권사 자동 등록: {} ({})", orgCode, orgName);
        }
    }

    private void seed(ScenarioDefinition def, String profileCode) {
        for (ScenarioDefinition.PriceSeed p : def.getPrices()) {
            MockPrice price = new MockPrice();
            price.setProfileCode(profileCode);
            price.setProdCode(p.getProdCode());
            price.setProdName(p.getProdName());
            price.setProdType(p.getProdType());
            price.setExCode(p.getExCode());
            price.setMarketType(p.getMarketType());
            // 시나리오 JSON의 값은 폴백이다. 네이버 시세 조회가 되면 실제 현재가로 덮어써서
            // 콘솔에 표시되는 현재가가 그럴듯하게 오늘 시세와 비슷해지도록 한다.
            price.setCurrentPrice(marketQuoteService.fetchCurrentPrice(p.getProdCode())
                    .orElse(p.getCurrentPrice()));
            priceMapper.insert(price);
        }

        for (ScenarioDefinition.AccountSeed a : def.getAccounts()) {
            MockAccount account = new MockAccount();
            account.setProfileCode(profileCode);
            account.setOrgCode(def.getOrgCode());
            account.setAccountNum(a.getAccountNum());
            account.setAccountName(a.getAccountName());
            account.setAccountType(a.getAccountType());
            account.setIssueDate(a.getIssueDate());
            account.setCashBalance(a.getInitialCash());
            accountMapper.insert(account);
        }

        // 거래를 시간순으로 재생하면 보유수량·평균단가·예수금이 자동으로 맞춰진다.
        List<ScenarioDefinition.TradeSeed> trades = new ArrayList<>(def.getTrades());
        trades.sort(Comparator.comparing(ScenarioDefinition.TradeSeed::getTradedAt));
        // OrderService.execute()는 건마다 @Transactional이라, 그냥 반복 호출하면 거래마다 별도
        // 트랜잭션이 커밋된다 — 시나리오가 거래 수백 건이면 커밋 수백 번이 순차로 도는 셈이라
        // 느리다. 여기서 하나의 트랜잭션으로 묶어서 재생 전체가 한 번에 커밋되게 한다(네트워크
        // 호출이 섞인 가격 시딩은 위에서 이미 끝났으므로, 트랜잭션은 DB 작업만 감싼다).
        transactionTemplate.executeWithoutResult(status -> {
            for (ScenarioDefinition.TradeSeed t : trades) {
                orderService.execute(profileCode, t.getAccountNum(), t.getProdCode(), t.getSide(),
                        t.getQuantity(), t.getPrice(), t.getTradedAt());
            }
        });
    }
}
