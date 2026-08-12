package com.investory.mockbroker.web;

import com.investory.mockbroker.dto.MockApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/** 모든 오류를 마이데이터 응답 형태(rsp_code / rsp_msg)로 통일해서 내려준다. */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MockApiException.class)
    public ResponseEntity<Map<String, Object>> handleMockApi(MockApiException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rsp_code", e.getRspCode());
        body.put("rsp_msg", e.getMessage());
        return ResponseEntity.status(e.getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rsp_code", "50000");
        body.put("rsp_msg", "목업 서버 내부 오류: " + e.getMessage());
        return ResponseEntity.status(500).body(body);
    }
}
