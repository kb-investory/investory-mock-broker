package com.investory.mockbroker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investory.mockbroker.domain.MockPrice;
import com.investory.mockbroker.mapper.PriceMapper;
import com.investory.mockbroker.seed.ScenarioDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 코스피 전 종목의 "코드·이름"만 담아두는 마스터 목록.
 *
 * resources/kospi_stocks.json에는 종목코드·종목명(+부팅 시점 캡처해둔 폴백 가격)만 있고,
 * 이걸 유저 앞으로 미리 깔아두지 않는다 — 유저 한 명당 900여개를 실시간 조회하며 저장하면
 * 로그인·회원가입·초기화가 한참 걸린다. 대신 실제로 화면에 뜨거나(조회) 거래되는 종목만
 * 그 순간 네이버에서 가져와 그 유저 앞으로 딱 한 번 활성화(activate)한다.
 */
@Service
public class StockMasterService {

    private static final Logger log = LoggerFactory.getLogger(StockMasterService.class);
    private static final String KOSPI_STOCKS_PATH = "classpath:kospi_stocks.json";

    private final PriceMapper priceMapper;
    private final MarketQuoteService marketQuoteService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, ScenarioDefinition.PriceSeed> byCode = new LinkedHashMap<>();

    @Autowired
    public StockMasterService(PriceMapper priceMapper, MarketQuoteService marketQuoteService) {
        this.priceMapper = priceMapper;
        this.marketQuoteService = marketQuoteService;
    }

    @PostConstruct
    public void init() {
        try (InputStream in = new PathMatchingResourcePatternResolver()
                .getResource(KOSPI_STOCKS_PATH).getInputStream()) {
            ScenarioDefinition.PriceSeed[] rows = objectMapper.readValue(in, ScenarioDefinition.PriceSeed[].class);
            Map<String, ScenarioDefinition.PriceSeed> loaded = new LinkedHashMap<>();
            for (ScenarioDefinition.PriceSeed row : rows) {
                loaded.put(row.getProdCode(), row);
            }
            byCode = loaded;
            log.info("코스피 종목 마스터 적재: {}건 (코드·이름만, 가격은 조회 시점에 실시간으로)", byCode.size());
        } catch (Exception e) {
            log.warn("코스피 종목 마스터를 읽지 못했습니다. 시드 JSON에 직접 정의된 종목만 쓸 수 있습니다: {}", e.toString());
        }
    }

    /** 콘솔 종목 선택창을 채우는 용도. 코드·이름만 돌려주고 네트워크 호출은 하지 않는다. */
    public List<ScenarioDefinition.PriceSeed> listAll() {
        return new ArrayList<>(byCode.values());
    }

    /** 종목코드로 마스터 목록에서 코드·이름·시장구분을 찾는다. 없으면 null. */
    public ScenarioDefinition.PriceSeed findByCode(String prodCode) {
        return byCode.get(prodCode);
    }

    /**
     * 이 유저 앞으로 이 종목의 현재가를 확인한다.
     * 이미 조회·거래된 적 있으면 저장된 값을 그대로 돌려주고, 처음 보는 종목이면 지금 네이버에서
     * 실시간으로 가져와 그 순간 값으로 활성화한다 (실패하면 마스터 목록의 폴백 가격을 쓴다).
     * 마스터 목록에도 없는 코드면 null.
     */
    public synchronized MockPrice ensureActivated(String profileCode, String prodCode) {
        MockPrice existing = priceMapper.findByProdCode(profileCode, prodCode);
        if (existing != null) {
            return existing;
        }
        ScenarioDefinition.PriceSeed master = byCode.get(prodCode);
        if (master == null) {
            return null;
        }
        MockPrice price = new MockPrice();
        price.setProfileCode(profileCode);
        price.setProdCode(master.getProdCode());
        price.setProdName(master.getProdName());
        price.setProdType(master.getProdType());
        price.setExCode(master.getExCode());
        price.setMarketType(master.getMarketType());
        price.setCurrentPrice(marketQuoteService.fetchCurrentPrice(prodCode).orElse(master.getCurrentPrice()));
        priceMapper.insert(price);
        return price;
    }
}
