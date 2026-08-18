package com.investory.mockbroker.controller;

import com.investory.mockbroker.domain.MockConnection;
import com.investory.mockbroker.domain.MockOrg;
import com.investory.mockbroker.domain.MockUser;
import com.investory.mockbroker.dto.AdminCreateUserRequest;
import com.investory.mockbroker.dto.ApplyScenarioRequest;
import com.investory.mockbroker.dto.GenerateScenarioRequest;
import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.mapper.AccountMapper;
import com.investory.mockbroker.mapper.ConnectionMapper;
import com.investory.mockbroker.mapper.HoldingMapper;
import com.investory.mockbroker.mapper.OrgMapper;
import com.investory.mockbroker.mapper.PriceMapper;
import com.investory.mockbroker.mapper.TransactionMapper;
import com.investory.mockbroker.mapper.UserMapper;
import com.investory.mockbroker.seed.ScenarioDefinition;
import com.investory.mockbroker.service.AccessTokenStore;
import com.investory.mockbroker.service.AdminTokenStore;
import com.investory.mockbroker.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * 관제 콘솔(connections.html) 전용 관리자 로그인 + 전체 커넥션 조회 + 유저 생성/관리 +
 * 시나리오·템플릿 적용/관리.
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
    private final AccessTokenStore accessTokenStore;
    private final ConnectionMapper connectionMapper;
    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final PriceMapper priceMapper;
    private final TransactionMapper transactionMapper;
    private final ScenarioService scenarioService;
    private final OrgMapper orgMapper;

    @Autowired
    public AdminController(AdminTokenStore tokenStore, AccessTokenStore accessTokenStore,
                           ConnectionMapper connectionMapper, UserMapper userMapper,
                           AccountMapper accountMapper, HoldingMapper holdingMapper,
                           PriceMapper priceMapper, TransactionMapper transactionMapper,
                           ScenarioService scenarioService, OrgMapper orgMapper) {
        this.tokenStore = tokenStore;
        this.accessTokenStore = accessTokenStore;
        this.connectionMapper = connectionMapper;
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.holdingMapper = holdingMapper;
        this.priceMapper = priceMapper;
        this.transactionMapper = transactionMapper;
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

    /** 번들된 시나리오 템플릿 + 관제 콘솔에서 저장한 DB 커스텀 템플릿 목록 (거래 패턴 선택창용). */
    @GetMapping("/templates")
    public List<Map<String, Object>> templates(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken) {
        requireAdmin(adminToken);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScenarioService.TemplateSummary def : scenarioService.listAllTemplates()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("templateId", def.getTemplateId());
            item.put("profileName", def.getProfileName());
            item.put("description", def.getDescription());
            item.put("source", def.getSource());
            result.add(item);
        }
        return result;
    }

    /** 커스텀 시나리오 JSON을 재사용 가능한 템플릿으로 DB에 저장한다. 번들 파일 템플릿과 같은 templateId는 거부된다. */
    @PostMapping("/templates")
    public Map<String, Object> saveTemplate(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken,
            @RequestBody ScenarioDefinition scenario) {
        requireAdmin(adminToken);
        scenarioService.saveCustomTemplate(scenario);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateId", scenario.getTemplateId());
        body.put("profileName", scenario.getProfileName());
        body.put("message", "템플릿을 저장했습니다.");
        return body;
    }

    /**
     * 종목별 실제 과거 종가로 매수 거래를 채운 시나리오 템플릿을 만들어 DB에 저장한다(저장만 하고
     * 유저에게 적용하지는 않는다 — 적용은 "유저 생성"/"유저 관리"에서 별도로 한다). 본 서비스
     * 분석 기능이 요구하는 최소 N일치 거래이력 테스트 데이터를 준비하는 용도.
     */
    @PostMapping("/templates/generate")
    public Map<String, Object> generateTemplate(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken,
            @RequestBody GenerateScenarioRequest request) {
        requireAdmin(adminToken);
        ScenarioDefinition def = scenarioService.generateHistoricalTemplate(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateId", def.getTemplateId());
        body.put("profileName", def.getProfileName());
        body.put("tradeCount", def.getTrades().size());
        body.put("message", "과거 시세 기반 템플릿을 생성했습니다.");
        return body;
    }

    /** DB 커스텀 템플릿만 삭제 가능하다 (번들 파일 템플릿은 거부됨). */
    @DeleteMapping("/templates/{templateId}")
    public Map<String, Object> deleteTemplate(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken,
            @PathVariable String templateId) {
        requireAdmin(adminToken);
        scenarioService.deleteCustomTemplate(templateId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateId", templateId);
        body.put("message", "템플릿을 삭제했습니다.");
        return body;
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

        ScenarioDefinition def = resolveDefinition(request.getTemplateId(), request.getScenario());
        MockUser user = scenarioService.applyScenario(request.getLoginId(), def);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginId", user.getLoginId());
        body.put("orgCode", user.getOrgCode());
        body.put("orgName", user.getOrgName());
        return body;
    }

    /**
     * 유저부터 만들고(기본 org/계좌), templateId나 scenario가 있으면 바로 이어서 적용까지 한 번에
     * 한다. /mock/auth/register와 달리 systemKey가 필요 없다 (이미 admin 토큰으로 인증됨).
     */
    @PostMapping("/users")
    public Map<String, Object> createUser(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken,
            @RequestBody AdminCreateUserRequest request) {
        requireAdmin(adminToken);
        if (request.getLoginId() == null || request.getLoginId().isEmpty()
                || request.getLoginPassword() == null || request.getLoginPassword().isEmpty()) {
            throw MockApiException.badRequest("loginId와 loginPassword는 필수입니다.");
        }

        MockUser user = scenarioService.register(request.getLoginId(), request.getLoginPassword());
        if ((request.getTemplateId() != null && !request.getTemplateId().isEmpty()) || request.getScenario() != null) {
            ScenarioDefinition def = resolveDefinition(request.getTemplateId(), request.getScenario());
            user = scenarioService.applyScenario(request.getLoginId(), def);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginId", user.getLoginId());
        body.put("orgCode", user.getOrgCode());
        body.put("orgName", user.getOrgName());
        return body;
    }

    /** 관제 콘솔의 유저 관리 페이지용 — 전체 데모 유저 목록. */
    @GetMapping("/users")
    public List<Map<String, Object>> users(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken) {
        requireAdmin(adminToken);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MockUser user : userMapper.findAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("loginId", user.getLoginId());
            item.put("orgName", user.getOrgName());
            item.put("scenarioApplied", user.getScenarioJson() != null);
            item.put("appliedTemplateId", scenarioService.appliedTemplateIdOf(user));
            result.add(item);
        }
        return result;
    }

    /**
     * 유저와 그 유저의 계좌·보유종목·거래내역·시세·커넥션을 전부 지운다. 살아있는 accessToken이
     * 있으면 같이 무효화한다.
     */
    @DeleteMapping("/users/{loginId}")
    public Map<String, Object> deleteUser(
            @RequestHeader(value = "x-admin-token", required = false) String adminToken,
            @PathVariable String loginId) {
        requireAdmin(adminToken);
        MockUser user = userMapper.findByLoginId(loginId);
        if (user == null) {
            throw MockApiException.notFound("등록되지 않은 유저입니다: " + loginId);
        }
        String profileCode = user.getProfileCode();

        transactionMapper.deleteByProfileCode(profileCode);
        holdingMapper.deleteByProfileCode(profileCode);
        accountMapper.deleteByProfileCode(profileCode);
        priceMapper.deleteByProfileCode(profileCode);
        connectionMapper.deleteByProfileCode(profileCode);
        accessTokenStore.revoke(profileCode);
        userMapper.delete(profileCode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginId", loginId);
        body.put("message", "유저를 삭제했습니다.");
        return body;
    }

    /** templateId(번들/DB 템플릿 조회)와 scenario(직접 입력) 중 하나를 ScenarioDefinition으로 해석한다. templateId가 우선한다. */
    private ScenarioDefinition resolveDefinition(String templateId, ScenarioDefinition scenario) {
        if (templateId != null && !templateId.isEmpty()) {
            return scenarioService.getTemplate(templateId);
        }
        if (scenario != null) {
            return scenario;
        }
        throw MockApiException.badRequest("templateId 또는 scenario 중 하나는 필요합니다.");
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
