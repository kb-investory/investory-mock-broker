package com.investory.mockbroker.controller;

import com.investory.mockbroker.domain.MockAccount;
import com.investory.mockbroker.domain.MockUser;
import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.mapper.AccountMapper;
import com.investory.mockbroker.mapper.UserMapper;
import com.investory.mockbroker.service.AccessTokenStore;
import com.investory.mockbroker.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 외부 연동 요청의 로그인/회원가입 진입점.
 *
 * 실제 마이데이터의 통합인증/개별인증을 흉내낸다 — loginId/loginPassword로 로그인하면
 * 이 유저와 묶인 connectionId, 그리고 이후 /v2/invest/**·/mock/** 호출에 쓸 accessToken을
 * 함께 내려준다. "시나리오"라는 개념은 외부에 노출되지 않는다 — 어느 데모 유저로 로그인하느냐가
 * 곧 어떤 데이터를 보게 되는지를 결정한다.
 */
@RestController
@RequestMapping("/mock/auth")
public class AuthController {

    /**
     * 아무나 회원가입하지 못하도록 거는 최소한의 문. 실제 보안장치가 아니라, 데모 서버에
     * 무작위로 계정이 쌓이는 걸 막는 용도다. .env의 MOCKBROKER_SIGNUP_KEY로 바꿀 수 있다.
     */
    @Value("${MOCKBROKER_SIGNUP_KEY}")
    private String signupKey;

    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final AccessTokenStore tokenStore;
    private final ScenarioService scenarioService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public AuthController(UserMapper userMapper, AccountMapper accountMapper,
                          AccessTokenStore tokenStore, ScenarioService scenarioService) {
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.tokenStore = tokenStore;
        this.scenarioService = scenarioService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> request) {
        String loginId = asString(request.get("loginId"));
        String loginPassword = asString(request.get("loginPassword"));
        if (loginId == null || loginId.isEmpty() || loginPassword == null || loginPassword.isEmpty()) {
            throw MockApiException.badRequest("loginId와 loginPassword는 필수입니다.");
        }

        MockUser user = userMapper.findByLoginId(loginId);
        // 계정 존재 여부를 구분해서 알려주지 않는다 — 일반적인 로그인 보안 관례.
        if (user == null || !passwordEncoder.matches(loginPassword, user.getLoginPasswordHash())) {
            throw MockApiException.unauthorized("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return connectionResponse(user);
    }

    /** loginId/loginPassword/systemKey로 회원가입한다. 가입 즉시 로그인 응답과 동일한 형태로 토큰까지 내려준다. */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> request) {
        String loginId = asString(request.get("loginId"));
        String loginPassword = asString(request.get("loginPassword"));
        String systemKey = asString(request.get("systemKey"));
        if (loginId == null || loginId.isEmpty() || loginPassword == null || loginPassword.isEmpty()) {
            throw MockApiException.badRequest("loginId와 loginPassword는 필수입니다.");
        }
        if (systemKey == null || !systemKey.equals(signupKey)) {
            throw MockApiException.unauthorized("시스템 암호키가 올바르지 않습니다.");
        }

        MockUser user = scenarioService.register(loginId, loginPassword);
        return connectionResponse(user);
    }

    private Map<String, Object> connectionResponse(MockUser user) {
        String accessToken = tokenStore.issue(user.getProfileCode());

        List<Map<String, Object>> accounts = new ArrayList<>();
        for (MockAccount a : accountMapper.findByOrgCode(user.getProfileCode(), user.getOrgCode())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("accountNum", a.getAccountNum());
            item.put("accountName", a.getAccountName());
            item.put("accountType", a.getAccountType());
            item.put("issueDate", a.getIssueDate());
            item.put("cashBalance", a.getCashBalance());
            accounts.add(item);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectionId", user.getConnectionId());
        body.put("accessToken", accessToken);
        body.put("tokenType", "Bearer");
        body.put("orgCode", user.getOrgCode());
        body.put("orgName", user.getOrgName());
        body.put("accounts", accounts);
        return body;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
