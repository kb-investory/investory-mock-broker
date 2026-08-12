package com.investory.mockbroker.dto;

/**
 * 목업 서버가 의도적으로 발생시키는 업무 예외.
 * rspCode는 마이데이터 응답 규격의 rsp_code 자리에 그대로 실려나간다.
 */
public class MockApiException extends RuntimeException {

    private final String rspCode;
    private final int httpStatus;

    public MockApiException(String rspCode, int httpStatus, String message) {
        super(message);
        this.rspCode = rspCode;
        this.httpStatus = httpStatus;
    }

    public static MockApiException notFound(String message) {
        return new MockApiException("40401", 404, message);
    }

    public static MockApiException badRequest(String message) {
        return new MockApiException("40001", 400, message);
    }

    public static MockApiException unauthorized(String message) {
        return new MockApiException("40101", 401, message);
    }

    /** 예수금 부족 */
    public static MockApiException insufficientCash(String message) {
        return new MockApiException("50020", 422, message);
    }

    /** 보유수량 부족 */
    public static MockApiException insufficientHolding(String message) {
        return new MockApiException("50021", 422, message);
    }

    /** 가격제한폭(상한가/하한가) 초과 */
    public static MockApiException priceLimitExceeded(String message) {
        return new MockApiException("50022", 422, message);
    }

    public String getRspCode() { return rspCode; }
    public int getHttpStatus() { return httpStatus; }
}
