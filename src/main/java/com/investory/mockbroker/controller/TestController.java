package com.investory.mockbroker.controller;

import com.investory.mockbroker.domain.MockAccount;
import com.investory.mockbroker.domain.MockUser;
import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.dto.OrderResult;
import com.investory.mockbroker.dto.TestTradeRequest;
import com.investory.mockbroker.mapper.AccountMapper;
import com.investory.mockbroker.mapper.UserMapper;
import com.investory.mockbroker.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 메인 서버 연동 테스트 편의용 API. 실제 마이데이터 규격에도, 콘솔 화면에도 없는 지름길이다 —
 * accessToken 발급 없이 loginId만으로 "유저가 방금 증권사 앱에서 거래를 체결했다"를 만들어낸다.
 * 메인 서버 쪽 거래내역 동기화 API를 이어서 호출하면, 로그인 단계 없이 한 번의 호출로
 * "거래 발생 → 메인 서버 반영" 전체 흐름을 스크립트로 재현할 수 있다.
 *
 * 데모 서버 전용 지름길이라 인증을 두지 않았다 — 실제 계정 자산에 영향을 주지 않는 목업
 * 데이터에만 작용한다.
 */
@RestController
@RequestMapping("/mock/test")
public class TestController {

    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final OrderService orderService;

    @Autowired
    public TestController(UserMapper userMapper, AccountMapper accountMapper, OrderService orderService) {
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.orderService = orderService;
    }

    @PostMapping("/trade")
    public OrderResult trade(@RequestBody TestTradeRequest request) {
        String loginId = request.getLoginId();
        if (loginId == null || loginId.isEmpty()) {
            throw MockApiException.badRequest("loginId는 필수입니다.");
        }
        MockUser user = userMapper.findByLoginId(loginId);
        if (user == null) {
            throw MockApiException.notFound("등록되지 않은 유저입니다: " + loginId);
        }

        String accountNum = request.getAccountNum();
        if (accountNum == null || accountNum.isEmpty()) {
            List<MockAccount> accounts = accountMapper.findByOrgCode(user.getProfileCode(), user.getOrgCode());
            if (accounts.isEmpty()) {
                throw MockApiException.badRequest("이 유저에게 등록된 계좌가 없습니다: " + loginId);
            }
            accountNum = accounts.get(0).getAccountNum();
        }

        return orderService.execute(user.getProfileCode(), accountNum, request.getProdCode(),
                request.getSide(), request.getQuantity(), request.getPrice(), request.getTradedAt());
    }
}
