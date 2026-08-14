package com.investory.mockbroker.controller;

import com.investory.mockbroker.domain.MockConnection;
import com.investory.mockbroker.domain.MockOrg;
import com.investory.mockbroker.domain.MockUser;
import com.investory.mockbroker.dto.ApplyScenarioRequest;
import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.mapper.ConnectionMapper;
import com.investory.mockbroker.mapper.OrgMapper;
import com.investory.mockbroker.mapper.UserMapper;
import com.investory.mockbroker.seed.ScenarioDefinition;
import com.investory.mockbroker.service.AdminTokenStore;
import com.investory.mockbroker.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * 관제 콘솔(connections.html) 전용 관리자 로그인 + 전체 커넥션 조회 + 시나리오 적용.
 *
 * client_id/secret(외부 서비스 인증)이나 accessToken(데모 유저 인증)과는 완전히 별개의 세 번째
 * 인증 경로다 — 이건 사람(운영자)이 데모 유저를 관리하는 용도라, 특정 유저나 특정 client에
 * 스코프되지 않고 전체를 본다. 실제 보안장치가 아니라 아무나 이 화면을 보지 못하게 막는
 * 최소한의 문이다.
 */
@RestController
@RequestMapping("/mock/admin")
public class AdminController {

    @Value("${MOCKBROKER_ADMIN_USER}")
    private String adminUser;
    @Value("${MOCKBROKER_ADMIN_PASSWORD}")
    private String adminPassword;

    private final AdminTokenStore tokenStore;
    private final ConnectionMapper connectionMapper;
    private final UserMapper userMapper;
    private final ScenarioService scenarioService;
    private final OrgMapper orgMapper;

    @Autowired
    public AdminController(AdminTokenStore tokenStore, ConnectionMapper connectionMapper, UserMapper userMapper,
                           ScenarioService scenarioService, OrgMapper orgMapper) {
        this.tokenStore = tokenStore;
        this.connectionMapper = connectionMapper;
        this.userMapper = userMapper;
        this.scenarioService = scenarioService;
        this.orgMapper = orgMapper;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> request) {
        String username = asString(request.get("username"));
        String password = asString(request.get("password"));
        if (username == null || password == null || !username.equals(adminUser) || !password.equals(adminPassword)) {
            throw MockApiException.unauthorized("관리자 아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("adminToken", tokenStore.issue());
        return body;
    }

    /** 전체 유저의 커넥션 발급 현황 (client_id 무관, 최신순). */
    @GetMapping("/connections")
    public List<Map<String, Object>> connections(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken) {
        requireAdmin(adminToken);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MockConnection c : connectionMapper.findAll()) {
            MockUser user = userMapper.findByProfileCode(c.getProfileCode());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("connectionId", c.getConnectionId());
            item.put("clientId", c.getClientId());
            item.put("loginId", user == null ? null : user.getLoginId());
            item.put("orgName", user == null ? null : user.getOrgName());
            item.put("createdAt", c.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    /** 번들된 시나리오 템플릿 목록 (거래 패턴 선택창용, 자격증명 없음). */
    @GetMapping("/templates")
    public List<Map<String, Object>> templates(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken) {
        requireAdmin(adminToken);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScenarioDefinition def : scenarioService.listTemplates()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("templateId", def.getTemplateId());
            item.put("profileName", def.getProfileName());
            item.put("description", def.getDescription());
            result.add(item);
        }
        return result;
    }

    /** 등록된 증권사(org) 전체 목록. */
    @GetMapping("/orgs")
    public List<Map<String, Object>> orgs(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken) {
        requireAdmin(adminToken);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MockOrg org : orgMapper.findAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orgCode", org.getOrgCode());
            item.put("orgName", org.getOrgName());
            result.add(item);
        }
        return result;
    }

    /** 새 증권사를 등록한다. 배포 없이 콘솔에서 바로 목록을 늘릴 수 있게 하기 위함. */
    @PostMapping("/orgs")
    public Map<String, Object> createOrg(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken,
            @RequestBody Map<String, Object> request) {
        requireAdmin(adminToken);
        String orgCode = asString(request.get("orgCode"));
        String orgName = asString(request.get("orgName"));
        if (orgCode == null || orgCode.isEmpty() || orgName == null || orgName.isEmpty()) {
            throw MockApiException.badRequest("orgCode와 orgName은 필수입니다.");
        }
        if (orgMapper.findByOrgCode(orgCode) != null) {
            throw MockApiException.badRequest("이미 등록된 증권사 코드입니다: " + orgCode);
        }
        MockOrg org = new MockOrg();
        org.setOrgCode(orgCode);
        org.setOrgName(orgName);
        orgMapper.insert(org);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orgCode", org.getOrgCode());
        body.put("orgName", org.getOrgName());
        return body;
    }

    /**
     * 이미 존재하는 유저(loginId)에게 템플릿 또는 직접 입력한 JSON을 적용해 계좌·시세·거래이력을
     * 만든다. 유저 자체는 미리 있어야 한다 (예: /mock/auth/register로 만들어둔 뒤 이 API로
     * 거래이력만 얹는 방식).
     */
    @PostMapping("/scenarios")
    public Map<String, Object> applyScenario(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken,
            @RequestBody ApplyScenarioRequest request) {
        requireAdmin(adminToken);
        if (request.getLoginId() == null || request.getLoginId().isEmpty()) {
            throw MockApiException.badRequest("loginId는 필수입니다.");
        }

        ScenarioDefinition def;
        if (request.getTemplateId() != null && !request.getTemplateId().isEmpty()) {
            def = scenarioService.getTemplate(request.getTemplateId());
        } else if (request.getScenario() != null) {
            def = request.getScenario();
        } else {
            throw MockApiException.badRequest("templateId 또는 scenario 중 하나는 필요합니다.");
        }

        MockUser user = scenarioService.applyScenario(request.getLoginId(), def);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginId", user.getLoginId());
        body.put("orgCode", user.getOrgCode());
        body.put("orgName", user.getOrgName());
        return body;
    }

    private void requireAdmin(String adminToken) {
        if (!tokenStore.isValid(adminToken)) {
            throw MockApiException.unauthorized("관리자 토큰이 유효하지 않습니다.");
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
