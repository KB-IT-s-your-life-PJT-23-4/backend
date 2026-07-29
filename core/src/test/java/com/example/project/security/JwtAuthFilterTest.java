package com.example.project.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthFilterTest {

    private static final String SECRET_KEY =
            "test-secret-key-for-jwt-auth-filter-must-be-at-least-32-bytes";

    private JwtProvider jwtProvider;
    private TokenRevocationStore tokenRevocationStore;
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET_KEY, 60_000L, 120_000L);
        tokenRevocationStore = new TokenRevocationStore();
        jwtAuthFilter = new JwtAuthFilter(jwtProvider, tokenRevocationStore);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Access Token으로 인증 정보를 등록한다")
    void authenticateAccessToken() throws ServletException, IOException {
        String accessToken = jwtProvider.createAccessToken("1", "USER");

        executeFilter(accessToken);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("1", authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_USER")));
    }

    @Test
    @DisplayName("Refresh Token은 API 인증에 사용할 수 없다")
    void rejectRefreshToken() throws ServletException, IOException {
        executeFilter(jwtProvider.createRefreshToken("1"));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("폐기된 Access Token은 인증에 사용할 수 없다")
    void rejectRevokedAccessToken() throws ServletException, IOException {
        String accessToken = jwtProvider.createAccessToken("1", "USER");
        tokenRevocationStore.revoke(accessToken, jwtProvider.getExpiration(accessToken));

        executeFilter(accessToken);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void executeFilter(String token) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        jwtAuthFilter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );
    }
}
