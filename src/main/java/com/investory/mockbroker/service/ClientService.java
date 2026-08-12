package com.investory.mockbroker.service;

import com.investory.mockbroker.domain.MockClient;
import com.investory.mockbroker.domain.MockConnection;
import com.investory.mockbroker.domain.MockUser;
import com.investory.mockbroker.dto.MockApiException;
import com.investory.mockbroker.mapper.ClientMapper;
import com.investory.mockbroker.mapper.ConnectionMapper;
import com.investory.mockbroker.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 외부 서비스(client)가 client_id/client_secret으로 자신을 증명하고, 유저의 loginId/loginPassword로
 * 그 유저와의 "커넥션"을 맺어 이후 요청에서 accessToken 없이 그 유저 데이터에 접근하게 해주는
 * 두 번째 인증 경로.
 *
 * 실제 마이데이터의 (정보수신기관 인증) + (개별인증으로 얻는 연결정보) 두 단계를 흉내낸다.
 * 기존 accessToken 경로(AuthController → AccessTokenStore)와는 완전히 독립적으로 동작하며,
 * 외부 서비스는 토큰 만료/재발급을 신경 쓸 필요 없이 client_id/secret + connectionId만
 * 계속 재사용하면 된다.
 */
@Service
public class ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientService.class);

    /** 기본 클라이언트(Investory 백엔드)를 부트스트랩할 때 쓰는 값. .env에서 바꿀 수 있다. */
    @Value("${MOCKBROKER_CLIENT_ID}")
    private String defaultClientId;
    @Value("${MOCKBROKER_CLIENT_SECRET}")
    private String defaultClientSecret;

    private final ClientMapper clientMapper;
    private final ConnectionMapper connectionMapper;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public ClientService(ClientMapper clientMapper, ConnectionMapper connectionMapper, UserMapper userMapper) {
        this.clientMapper = clientMapper;
        this.connectionMapper = connectionMapper;
        this.userMapper = userMapper;
    }

    /**
     * ScenarioService가 스키마를 초기화한 뒤 호출된다. 재배포/재기동 후에도 mock_client 행이
     * 그대로 남아있으므로, secret이 .env와 어긋나 있으면 매번 갱신해서 설정 변경이 실제로
     * 반영되게 한다.
     */
    public synchronized void ensureDefaultClient() {
        MockClient client = new MockClient();
        client.setClientId(defaultClientId);
        client.setClientSecret(defaultClientSecret);
        client.setClientName("Investory Backend");

        MockClient existing = clientMapper.findByClientId(defaultClientId);
        if (existing == null) {
            clientMapper.insert(client);
            log.info("기본 클라이언트 등록 완료: {}", defaultClientId);
        } else if (!existing.getClientSecret().equals(defaultClientSecret)
                || !existing.getClientName().equals(client.getClientName())) {
            clientMapper.update(client);
            log.info("기본 클라이언트 설정 변경 감지, 갱신 완료: {}", defaultClientId);
        }
    }

    /**
     * client_id/loginId/loginPassword로 유저 본인 확인 후, 이 클라이언트와 그 유저 사이의
     * 커넥션을 발급한다. 같은 클라이언트가 같은 유저에 대해 다시 요청해도 기존 커넥션을
     * 그대로 재사용한다 (AccessTokenStore.issue()와 동일한 재사용 원칙).
     */
    public synchronized MockConnection connect(String clientId, String clientSecret,
                                                String loginId, String loginPassword) {
        authenticateClient(clientId, clientSecret);

        if (loginId == null || loginId.isEmpty() || loginPassword == null || loginPassword.isEmpty()) {
            throw MockApiException.badRequest("loginId와 loginPassword는 필수입니다.");
        }
        MockUser user = userMapper.findByLoginId(loginId);
        if (user == null || !passwordEncoder.matches(loginPassword, user.getLoginPasswordHash())) {
            throw MockApiException.unauthorized("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        MockConnection existing = connectionMapper.findByClientIdAndProfileCode(clientId, user.getProfileCode());
        if (existing != null) {
            return existing;
        }

        MockConnection connection = new MockConnection();
        connection.setConnectionId(UUID.randomUUID().toString().replace("-", ""));
        connection.setClientId(clientId);
        connection.setProfileCode(user.getProfileCode());
        connectionMapper.insert(connection);
        return connection;
    }

    /** 이후 요청에서 x-client-id/x-client-secret/x-connection-id 헤더로 어느 유저의 요청인지 알아낸다. */
    public String profileCodeOfConnection(String clientId, String clientSecret, String connectionId) {
        authenticateClient(clientId, clientSecret);
        if (connectionId == null || connectionId.isEmpty()) {
            throw MockApiException.unauthorized("x-connection-id 헤더가 필요합니다.");
        }
        MockConnection connection = connectionMapper.findByConnectionId(connectionId);
        // 다른 클라이언트가 발급받은 connectionId를 추측해서 들이미는 경우까지 막는다.
        if (connection == null || !connection.getClientId().equals(clientId)) {
            throw MockApiException.unauthorized("유효하지 않은 커넥션입니다.");
        }
        return connection.getProfileCode();
    }

    private void authenticateClient(String clientId, String clientSecret) {
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            throw MockApiException.unauthorized("x-client-id/x-client-secret 헤더가 필요합니다.");
        }
        MockClient client = clientMapper.findByClientId(clientId);
        if (client == null || !client.getClientSecret().equals(clientSecret)) {
            throw MockApiException.unauthorized("클라이언트 인증에 실패했습니다.");
        }
    }
}
