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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 네이버 금융의 공개 시세 조회 API를 흉내낸 데모용 목업 서버 안에서, 종목 현재가·과거 일별
 * 종가를 "그럴듯하게" 보여주기 위해 실제 국내 시세를 참고용으로 가져온다.
 *
 * 실시간 체결 스트림이 아니라 단순 조회이며, 실패해도 시나리오 진행에는 영향이 없어야
 * 하므로 예외를 던지지 않고 빈 값을 돌려준다 (호출부는 항상 폴백을 준비해둔다).
 *
 * .env의 MOCKBROKER_QUOTE_ENABLED=false 로 조회 자체를 끌 수 있다 (오프라인 발표 환경 등).
 */
@Service
public class MarketQuoteService {

    private static final Logger log = LoggerFactory.getLogger(MarketQuoteService.class);
    private static final String QUOTE_URL = "https://polling.finance.naver.com/api/realtime/domestic/stock/";
    private static final String HISTORY_URL = "https://api.finance.naver.com/siseJson.naver";
    private static final String MARKET_CAP_URL = "https://finance.naver.com/sise/sise_market_sum.naver";
    private static final int TIMEOUT_MS = 2000;
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    /** 네이버 일별시세 응답이 완전한 JSON이 아니라(헤더 행만 홑따옴표) 데이터 행만 정규식으로 뽑아낸다. */
    private static final Pattern HISTORY_ROW = Pattern.compile(
            "\\[\"(\\d{8})\",\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),");
    private static final Pattern MARKET_CAP_ROW = Pattern.compile(
            "code=(\\d{6})\"[^>]*class=\"tltle\">([^<]+)</a>");
    /** 시가총액 순위 페이지에 같이 뜨는 ETF/ETN 상품명 접두어 — 완전한 목록은 아니고, 데모용
     *  테스트 데이터 생성에서 일반 종목만 고르기 위한 최선 노력(best-effort) 필터다. */
    private static final String[] FUND_NAME_PREFIXES = {
            "KODEX", "TIGER", "KBSTAR", "ACE", "SOL", "HANARO", "ARIRANG", "KOSEF",
            "PLUS", "TIMEFOLIO", "WOORI", "히어로즈", "파워", "마이다스",
    };

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

    /** 종목코드로 [from, to] 구간의 일별 종가를 날짜 오름차순으로 조회한다. 실패하면 빈 목록. */
    public List<HistoricalPrice> fetchHistoricalPrices(String prodCode, LocalDate from, LocalDate to) {
        if (!quoteEnabled) {
            return Collections.emptyList();
        }
        try {
            String url = HISTORY_URL + "?symbol=" + prodCode + "&requestType=1"
                    + "&startTime=" + from.format(BASIC_DATE) + "&endTime=" + to.format(BASIC_DATE)
                    + "&timeframe=day";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Referer", "https://finance.naver.com");
            headers.set("User-Agent", "Mozilla/5.0");
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            List<HistoricalPrice> result = new ArrayList<>();
            Matcher matcher = HISTORY_ROW.matcher(response.getBody());
            while (matcher.find()) {
                String date = matcher.group(1);
                BigDecimal closePrice = new BigDecimal(matcher.group(5));
                result.add(new HistoricalPrice(date, closePrice));
            }
            if (result.isEmpty()) {
                log.warn("네이버 일별시세 조회 결과가 없습니다: {} ({} ~ {})", prodCode, from, to);
            }
            return result;
        } catch (Exception e) {
            log.warn("네이버 일별시세 조회에 실패했습니다 ({}): {}", prodCode, e.toString());
            return Collections.emptyList();
        }
    }

    /**
     * 네이버 시가총액 순위 1페이지에서 상위 {limit}개 종목의 코드·이름을 가져온다(ETF/우선주는
     * 최선 노력으로 걸러낸다). 순위는 조회 시점마다 달라지므로 결과를 저장해두지 않는다 — 매번
     * 새로 조회한다. 실패하거나 조회가 꺼져 있으면 빈 목록.
     *
     * @param sosok 코스피 "0", 코스닥 "1"
     */
    public List<MarketCapEntry> fetchMarketCapTop(String sosok, int limit) {
        if (!quoteEnabled) {
            return Collections.emptyList();
        }
        try {
            String url = MARKET_CAP_URL + "?sosok=" + sosok + "&page=1";
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            List<MarketCapEntry> result = new ArrayList<>();
            Matcher matcher = MARKET_CAP_ROW.matcher(response.getBody());
            while (matcher.find() && result.size() < limit) {
                String code = matcher.group(1);
                String name = matcher.group(2).trim();
                if (isFundOrPreferred(name)) {
                    continue;
                }
                result.add(new MarketCapEntry(code, name));
            }
            if (result.isEmpty()) {
                log.warn("네이버 시가총액 순위 조회 결과가 없습니다 (sosok={})", sosok);
            }
            return result;
        } catch (Exception e) {
            log.warn("네이버 시가총액 순위 조회에 실패했습니다 (sosok={}): {}", sosok, e.toString());
            return Collections.emptyList();
        }
    }

    private boolean isFundOrPreferred(String name) {
        if (name.endsWith("우") || name.matches(".*우[A-Z]$")) {
            return true;
        }
        for (String prefix : FUND_NAME_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 종목의 특정일 종가. {date}는 yyyyMMdd. */
    public static class HistoricalPrice {
        private final String date;
        private final BigDecimal closePrice;

        public HistoricalPrice(String date, BigDecimal closePrice) {
            this.date = date;
            this.closePrice = closePrice;
        }

        public String getDate() { return date; }
        public BigDecimal getClosePrice() { return closePrice; }
    }

    /** 시가총액 순위 조회 결과 한 종목. */
    public static class MarketCapEntry {
        private final String code;
        private final String name;

        public MarketCapEntry(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }
}