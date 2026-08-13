package com.investory.mockbroker.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 접근토큰 보관소.
 *
 * 여러 데모 유저가 동시에 로그인해 있을 수 있으므로, 토큰은 유저(profileCode) 하나당 하나씩
 * 여러 개가 동시에 유효하다. 같은 유저가 다시 로그인해도 기존 토큰을 그대로 재사용한다 —
 * 재로그인 때문에 이미 그 토큰을 들고 있는 다른 클라이언트(예: Investory 백엔드)가 갑자기
 * 인증에서 튕기지 않도록 하기 위함이다.
 */
@Component
public class AccessTokenStore {

    private final Map<String, String> tokenToProfileCode = new ConcurrentHashMap<>();
    private final Map<String, String> profileCodeToToken = new ConcurrentHashMap<>();

    public synchronized String issue(String profileCode) {
        String existing = profileCodeToToken.get(profileCode);
        if (existing != null) {
            return existing;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToProfileCode.put(token, profileCode);
        profileCodeToToken.put(profileCode, token);
        return token;
    }

    public boolean isValid(String token) {
        return token != null && tokenToProfileCode.containsKey(token);
    }

    public String profileCodeOf(String token) {
        return tokenToProfileCode.get(token);
    }

    /** 유저 삭제 시, 그 유저 앞으로 발급된 토큰이 있으면 더 이상 유효하지 않도록 무효화한다. */
    public synchronized void revoke(String profileCode) {
        String token = profileCodeToToken.remove(profileCode);
        if (token != null) {
            tokenToProfileCode.remove(token);
        }
    }

    public void clear() {
        tokenToProfileCode.clear();
        profileCodeToToken.clear();
    }
}
