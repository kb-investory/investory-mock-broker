package com.investory.mockbroker.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관제 콘솔(connections.html) 로그인 토큰 보관소. 유저 하나당 토큰인 AccessTokenStore와 달리
 * 관리자 계정은 하나뿐이라 발급된 토큰을 그냥 집합으로만 관리한다 — 여러 탭에서 로그인해도
 * 서로의 토큰을 무효화하지 않는다.
 */
@Component
public class AdminTokenStore {

    private final Set<String> validTokens = ConcurrentHashMap.newKeySet();

    public String issue() {
        String token = UUID.randomUUID().toString().replace("-", "");
        validTokens.add(token);
        return token;
    }

    public boolean isValid(String token) {
        return token != null && validTokens.contains(token);
    }
}
