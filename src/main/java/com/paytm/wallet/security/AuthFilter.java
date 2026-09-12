package com.paytm.wallet.security;

import com.paytm.wallet.dto.ApiModels.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {
    public static final String USER_ID_ATTRIBUTE = "authenticatedUserId";
    private final Map<String, String> tokenUsers;
    private final ObjectMapper objectMapper;

    public AuthFilter(@Value("${app.auth.tokens:token-alice=alice,token-bob=bob,token-treasury=system-treasury}") String tokens,
                      ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.tokenUsers = Arrays.stream(tokens.split(","))
                .map(String::trim).filter(s -> s.contains("="))
                .map(s -> s.split("=", 2))
                .collect(Collectors.toUnmodifiableMap(parts -> parts[0], parts -> parts[1], (a, b) -> a));
    }

    @Override 
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/dashboard.html") || path.equals("/favicon.ico");
    }

    @Override 
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String user = header != null && header.startsWith("Bearer ") ? tokenUsers.get(header.substring(7)) : null;
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse("unauthorized", "Provide a valid Bearer token"));
            return;
        }
        request.setAttribute(USER_ID_ATTRIBUTE, user);
        chain.doFilter(request, response);
    }
}
