package com.example.project.security;

import com.example.project.admin.domain.AdminPrincipal;
import com.example.project.admin.service.AdminAuthorizationService;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Log4j2
public class AdminAuthorizationFilter extends OncePerRequestFilter {

    private static final AntPathRequestMatcher ADMIN_REQUEST_MATCHER =
            new AntPathRequestMatcher("/api/admin/**");

    private final AdminAuthorizationService adminAuthorizationService;

    public AdminAuthorizationFilter(AdminAuthorizationService adminAuthorizationService) {
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ADMIN_REQUEST_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            logAccess(request, null, "DENIED", null);
            filterChain.doFilter(request, response);
            return;
        }

        Optional<AdminPrincipal> currentUser =
                adminAuthorizationService.findCurrentUser(authentication.getPrincipal());

        if (currentUser.isEmpty()) {
            SecurityContextHolder.clearContext();
            logAccess(request, null, "DENIED", null);
            filterChain.doFilter(request, response);
            return;
        }

        AdminPrincipal principal = currentUser.get();
        List<SimpleGrantedAuthority> authorities = principal.role() == null
                ? List.of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));

        var databaseAuthentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities
        );
        databaseAuthentication.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(databaseAuthentication);

        String result = adminAuthorizationService.isAdminRole(principal.role()) ? "ALLOWED" : "DENIED";
        logAccess(request, principal.userId(), result, principal.role());
        filterChain.doFilter(request, response);
    }

    private void logAccess(
            HttpServletRequest request,
            Long userId,
            String result,
            String role
    ) {
        log.info(
                "ADMIN_ACCESS userId={} method={} uri={} result={} role={}",
                userId == null ? "-" : userId,
                request.getMethod(),
                request.getRequestURI(),
                result,
                role == null ? "-" : role
        );
    }
}
