package com.investory.mockbroker.web;

import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.service.AccessTokenStore;
import com.investory.mockbroker.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 요청을 인증하고, 그 요청이 가리키는 유저(profileCode)를 request attribute로 남긴다 —
 * 컨트롤러들이 이걸로 자기 데이터만 조회/조작하도록 스코프한다.
 *
 * 두 가지 인증 경로를 받아준다.
 *   1) Authorization: Bearer {accessToken} — /mock/auth/login으로 유저 본인이 로그인해서 받은 토큰.
 *   2) x-client-id/x-client-secret/x-connection-id — 외부 서비스가 /mock/system/connections로
 *      맺어둔 커넥션을 그대로 재사용하는 경로. accessToken 발급·재발급 없이 유저 데이터에 접근한다.
 * 둘 중 어느 쪽으로 인증됐는지는 컨트롤러 입장에서 알 필요가 없다 — 결국 같은 profileCode
 * attribute로 귀결되기 때문이다.
 *
 * x-api-tran-id는 실제 마이데이터 규격(/v2/invest/**)에만 있는 헤더라, servlet-context.xml에서
 * /mock/** 매핑에는 requireTranId=false로 등록해 요구하지 않는다.
 */
public class MyDataHeaderInterceptor implements HandlerInterceptor {

    public static final String PROFILE_CODE_ATTR = "profileCode";

    @Autowired
    private AccessTokenStore tokenStore;

    @Autowired
    private ClientService clientService;

    private boolean requireTranId = true;

    public void setRequireTranId(boolean requireTranId) {
        this.requireTranId = requireTranId;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String profileCode;
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            if (!tokenStore.isValid(token)) {
                throw MockApiException.unauthorized("유효하지 않은 접근토큰입니다.");
            }
            profileCode = tokenStore.profileCodeOf(token);
        } else {
            String clientId = request.getHeader("x-client-id");
            String clientSecret = request.getHeader("x-client-secret");
            String connectionId = request.getHeader("x-connection-id");
            if (clientId == null && clientSecret == null && connectionId == null) {
                throw MockApiException.unauthorized(
                        "Authorization 헤더에 Bearer 접근토큰이 필요하거나, "
                                + "x-client-id/x-client-secret/x-connection-id 헤더가 필요합니다.");
            }
            profileCode = clientService.profileCodeOfConnection(clientId, clientSecret, connectionId);
        }
        request.setAttribute(PROFILE_CODE_ATTR, profileCode);

        if (requireTranId) {
            String tranId = request.getHeader("x-api-tran-id");
            if (tranId == null || tranId.isEmpty()) {
                throw MockApiException.badRequest("x-api-tran-id 헤더는 필수입니다.");
            }
            response.setHeader("x-api-tran-id", tranId);
        }
        return true;
    }
}
