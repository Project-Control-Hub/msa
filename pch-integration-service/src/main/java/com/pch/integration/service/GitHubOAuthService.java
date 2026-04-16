package com.pch.integration.service;

import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import com.pch.integration.config.GitHubOAuthConfig;
import com.pch.integration.domain.VcsConnection;
import com.pch.integration.domain.VcsProvider;
import com.pch.integration.repository.VcsConnectionRepository;
import com.pch.integration.security.TokenEncryptor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GitHubOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthService.class);
    private static final String STATE_PREFIX = "github:oauth:state:";

    private final GitHubOAuthConfig oauthConfig;
    private final VcsConnectionRepository vcsConnectionRepository;
    private final TokenEncryptor tokenEncryptor;
    private final StringRedisTemplate redisTemplate;
    private final RestClient.Builder restClientBuilder;

    public String buildAuthorizationUrl(Long userId) {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(STATE_PREFIX + state, String.valueOf(userId), Duration.ofMinutes(10));

        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + oauthConfig.getClientId()
                + "&redirect_uri=" + oauthConfig.getRedirectUri()
                + "&scope=repo,read:user"
                + "&state=" + state;
    }

    @Transactional
    public void handleCallback(String code, String state) {
        String userIdStr = redisTemplate.opsForValue().getAndDelete(STATE_PREFIX + state);
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = Long.parseLong(userIdStr);

        String accessToken = exchangeCodeForToken(code);
        String encryptedToken = tokenEncryptor.encrypt(accessToken);

        vcsConnectionRepository.findByUserIdAndProvider(userId, VcsProvider.GITHUB)
                .ifPresentOrElse(
                        conn -> conn.updateToken(encryptedToken),
                        () -> vcsConnectionRepository.save(
                                VcsConnection.create(userId, VcsProvider.GITHUB, encryptedToken, "repo,read:user"))
                );

        log.info("GitHub OAuth 연결 완료: userId={}", userId);
    }

    @Transactional
    public void disconnect(Long userId) {
        vcsConnectionRepository.deleteByUserIdAndProvider(userId, VcsProvider.GITHUB);
        log.info("GitHub OAuth 연결 해제: userId={}", userId);
    }

    private String exchangeCodeForToken(String code) {
        RestClient client = restClientBuilder.build();
        @SuppressWarnings("unchecked")
        Map<String, String> response = client.post()
                .uri("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .body(Map.of(
                        "client_id", oauthConfig.getClientId(),
                        "client_secret", oauthConfig.getClientSecret(),
                        "code", code
                ))
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return response.get("access_token");
    }
}
