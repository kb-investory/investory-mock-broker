package com.investory.mockbroker.controller;

import com.investory.mockbroker.domain.MockAccount;
import com.investory.mockbroker.domain.MockConnection;
import com.investory.mockbroker.domain.MockOrg;
import com.investory.mockbroker.domain.MockUser;
import com.investory.mockbroker.mapper.AccountMapper;
import com.investory.mockbroker.mapper.OrgMapper;
import com.investory.mockbroker.mapper.UserMapper;
import com.investory.mockbroker.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 외부 서비스(client)가 client_id/client_secret으로 자신을 증명하고, 유저의 loginId/loginPassword로
 * 그 유저와의 커넥션을 맺는 진입점.
 *
 * /mock/auth/login과 달리 accessToken을 내려주지 않는다 — 이후 /v2/invest/**·/mock/** 요청은
 * 여기서 받은 connectionId를 x-client-id/x-client-secret과 함께 x-connection-id 헤더로 실어
 * 보내면 그 자체로 인증된다 (MyDataHeaderInterceptor 참고).
 */
@RestController
@RequestMapping("/mock/system")
public class SystemController {

    private final ClientService clientService;
    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final OrgMapper orgMapper;

    @Autowired
    public SystemController(ClientService clientService, UserMapper userMapper, AccountMapper accountMapper,
                            OrgMapper orgMapper) {
        this.clientService = clientService;
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.orgMapper = orgMapper;
    }

    /** 이 목업 서버가 알고 있는 증권사(org) 전체 목록. 백엔드가 자기 쪽 목록과 동기화할 때 쓴다. */
    @GetMapping("/orgs")
    public List<Map<String, Object>> orgs(@RequestHeader(value = "x-client-id", required = false) String clientId,
                                          @RequestHeader(value = "x-client-secret", required = false) String clientSecret) {
        clientService.authenticateClient(clientId, clientSecret);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MockOrg org : orgMapper.findAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orgCode", org.getOrgCode());
            item.put("orgName", org.getOrgName());
            result.add(item);
        }
        return result;
    }

    @PostMapping("/connections")
    public Map<String, Object> connect(@RequestHeader(value = "x-client-id", required = false) String clientId,
                                       @RequestHeader(value = "x-client-secret", required = false) String clientSecret,
                                       @RequestBody Map<String, Object> request) {
        String loginId = asString(request.get("loginId"));
        String loginPassword = asString(request.get("loginPassword"));
        MockConnection connection = clientService.connect(clientId, clientSecret, loginId, loginPassword);
        MockUser user = userMapper.findByProfileCode(connection.getProfileCode());

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
        body.put("connectionId", connection.getConnectionId());
        body.put("orgCode", user.getOrgCode());
        body.put("orgName", user.getOrgName());
        body.put("accounts", accounts);
        return body;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
