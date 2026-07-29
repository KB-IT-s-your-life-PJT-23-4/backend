package com.example.project.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";

    private final JwtProvider jwtProvider;
    private final TokenRevocationStore tokenRevocationStore;

    public JwtAuthFilter(JwtProvider jwtProvider, TokenRevocationStore tokenRevocationStore) {
        this.jwtProvider = jwtProvider;
        this.tokenRevocationStore = tokenRevocationStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain filterChain) throws ServletException, IOException {
        String token = JwtUtil.resolveAccessToken(req.getHeader(AUTH_HEADER));

        if (token != null
                && !tokenRevocationStore.isRevoked(token)
                && jwtProvider.isValidAccessToken(token)) {
            Claims claims = jwtProvider.parse(token);
            String userId = JwtUtil.getUserId(claims);
            String role = JwtUtil.getRole(claims);

            List<SimpleGrantedAuthority> authorities = role == null ?
                    List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));

            var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(req, res);
    }
}
