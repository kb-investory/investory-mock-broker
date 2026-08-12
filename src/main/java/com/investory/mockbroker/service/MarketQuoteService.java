package com.investory.mockbroker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 네이버 금융의 공개 시세 조회 API를 흉내낸 데모용 목업 서버 안에서, 종목 현재가를
 * "그럴듯하게" 보여주기 위해 실제 국내 시세를 참고용으로 가져온다.
 *
 * 실시간 체결 스트림이 아니라 단순 조회이며, 실패해도 시나리오 진행에는 영향이 없어야
 * 하므로 예외를 던지지 않고 빈 Optional을 돌려준다 (호출부는 항상 폴백 값을 준비해둔다).
 *
 * .env의 MOCKBROKER_QUOTE_ENABLED=false 로 조회 자체를 끌 수 있다 (오프라인 발표 환경 등).
 */
@Service
public class MarketQuoteService {

    private static final Logger log = LoggerFactory.getLogger(MarketQuoteService.class);
    private static final String QUOTE_URL = "https://polling.finance.naver.com/api/realtime/domestic/stock/";
    private static final int TIMEOUT_MS = 2000;

    @Value("${MOCKBROKER_QUOTE_ENABLED:true}")
    private boolean quoteEnabled;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MarketQuoteService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 종목코드(예: 005930)로 현재가를 조회한다. 실패하면 빈 값을 돌려준다. */
    public Optional<BigDecimal> fetchCurrentPrice(String prodCode) {
        if (!quoteEnabled) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Referer", "https://finance.naver.com");
            headers.set("User-Agent", "Mozilla/5.0");
            ResponseEntity<String> response = restTemplate.exchange(
                    QUOTE_URL + prodCode, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode datas = objectMapper.readTree(response.getBody()).path("datas");
            if (!datas.isArray() || datas.isEmpty()) {
                log.warn("네이버 시세 조회 결과가 없습니다: {}", prodCode);
                return Optional.empty();
            }
            String closePrice = datas.get(0).path("closePrice").asText(null);
            if (closePrice == null || closePrice.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new BigDecimal(closePrice.replace(",", "")));
        } catch (Exception e) {
            log.warn("네이버 시세 조회에 실패했습니다 ({}): {}", prodCode, e.toString());
            return Optional.empty();
        }
    }
}