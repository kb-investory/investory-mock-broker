package com.investory.mockbroker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investory.mockbroker.domain.MockAccount;
import com.investory.mockbroker.domain.MockPrice;
import com.investory.mockbroker.domain.MockUser;
import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.mapper.AccountMapper;
import com.investory.mockbroker.mapper.HoldingMapper;
import com.investory.mockbroker.mapper.PriceMapper;
import com.investory.mockbroker.mapper.TransactionMapper;
import com.investory.mockbroker.mapper.UserMapper;
import com.investory.mockbroker.seed.ScenarioDefinition;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final DataSource dataSource;
    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final PriceMapper priceMapper;
    private final HoldingMapper holdingMapper;
    private final TransactionMapper transactionMapper;
    private final OrderService orderService;
    private final MarketQuoteService marketQuoteService;
    private final ClientService clientService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final Map<String, ScenarioDefinition> scenarios = new LinkedHashMap<>();

    @Autowired
    public ScenarioService(DataSource dataSource, UserMapper userMapper, AccountMapper accountMapper,
                           PriceMapper priceMapper, HoldingMapper holdingMapper,
                           TransactionMapper transactionMapper, OrderService orderService,
                           MarketQuoteService marketQuoteService, ClientService clientService) {
        this.dataSource = dataSource;
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.priceMapper = priceMapper;
        this.holdingMapper = holdingMapper;
        this.transactionMapper = transactionMapper;
        this.orderService = orderService;
        this.marketQuoteService = marketQuoteService;
        this.clientService = clientService;
    }

    @PostConstruct
    public void init() {
        initSchema();
        clientService.ensureDefaultClient();
        loadScenarioFiles();
    }

    /** db/migration의 버전드 마이그레이션을 적용한다. 이미 적용된 버전은 Flyway가 알아서 건너뛴다. */
    private void initSchema() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
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

    public ScenarioDefinition getTemplate(String templateId) {
        ScenarioDefinition def = scenarios.get(templateId);
        if (def == null) {
            throw MockApiException.notFound("등록되지 않은 템플릿입니다: " + templateId);
        }
        return def;
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
        for (ScenarioDefinition.TradeSeed t : trades) {
            orderService.execute(profileCode, t.getAccountNum(), t.getProdCode(), t.getSide(),
                    t.getQuantity(), t.getPrice(), t.getTradedAt());
        }
    }
}
