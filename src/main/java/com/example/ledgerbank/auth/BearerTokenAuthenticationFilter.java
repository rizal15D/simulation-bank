package com.example.ledgerbank.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private final AuthSessionService sessions;
    private final SecurityErrorWriter errors;

    public BearerTokenAuthenticationFilter(AuthSessionService sessions, SecurityErrorWriter errors) {
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith("Bearer ") || authorization.length() == "Bearer ".length()) {
            errors.write(response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "A valid bearer token is required");
            return;
        }
        try {
            BankingPrincipal principal = sessions.find(authorization.substring("Bearer ".length())).orElse(null);
            if (principal == null) {
                errors.write(response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "Bearer token is invalid or expired");
                return;
            }
            var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            chain.doFilter(request, response);
        } catch (DataAccessException unavailable) {
            errors.write(response, HttpStatus.SERVICE_UNAVAILABLE.value(), "AUTH_SERVICE_UNAVAILABLE",
                    "Authentication session storage is unavailable");
        }
    }
}
