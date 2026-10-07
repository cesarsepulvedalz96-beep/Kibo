package com.stockflow.auth.infrastructure;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded in-process limiter. AWS WAF supplies a shared limit when scaling out. */
public class LoginRateLimiter extends OncePerRequestFilter {
    private record Window(long startsAt, int attempts) {}

    private final Map<String, Window> windows = new HashMap<>();
    private final TrustedClientAddress addresses;

    public LoginRateLimiter(TrustedClientAddress addresses) {
        this.addresses = addresses;
    }

    private synchronized boolean allowed(String address) {
        long now = Instant.now().getEpochSecond();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startsAt() >= 900);
        var current = windows.get(address);
        return current == null || current.attempts() < 20;
    }

    private synchronized void failed(String address) {
        long now = Instant.now().getEpochSecond();
        var current = windows.get(address);
        if (current == null) {
            if (windows.size() < 10000) windows.put(address, new Window(now, 1));
        } else {
            windows.put(address, new Window(current.startsAt(), current.attempts() + 1));
        }
    }

    private synchronized void succeeded(String address) {
        windows.remove(address);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getMethod().equals("POST")
                || !request.getServletPath().equals("/api/v1/auth/login")) {
            chain.doFilter(request, response);
            return;
        }
        String address = addresses.resolve(request);
        if (!allowed(address)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "900");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\"}");
            return;
        }
        chain.doFilter(request, response);
        if (response.getStatus() == 401) failed(address);
        else if (response.getStatus() < 400) succeeded(address);
    }
}
