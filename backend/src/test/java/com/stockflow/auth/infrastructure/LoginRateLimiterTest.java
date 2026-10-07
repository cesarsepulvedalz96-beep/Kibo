package com.stockflow.auth.infrastructure;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginRateLimiterTest {
    private MockHttpServletRequest request(String remote, String client) {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        request.setRemoteAddr(remote);
        if (client != null) request.addHeader("X-StockFlow-Client-IP", client);
        return request;
    }

    private int attempt(LoginRateLimiter limiter, String remote, String client) throws Exception {
        var response = new MockHttpServletResponse();
        limiter.doFilter(
                request(remote, client),
                response,
                (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401));
        return response.getStatus();
    }

    @Test
    void trustedProxyClientsHaveIndependentLimits() throws Exception {
        var limiter = new LoginRateLimiter(new TrustedClientAddress("172.30.0.10"));
        for (int i = 0; i < 20; i++)
            assertThat(attempt(limiter, "172.30.0.10", "203.0.113.1")).isEqualTo(401);
        assertThat(attempt(limiter, "172.30.0.10", "203.0.113.1")).isEqualTo(429);
        assertThat(attempt(limiter, "172.30.0.10", "203.0.113.2")).isEqualTo(401);
    }

    @Test
    void untrustedClientCannotEvadeLimitBySpoofingHeaders() throws Exception {
        var limiter = new LoginRateLimiter(new TrustedClientAddress("172.30.0.10"));
        for (int i = 0; i < 20; i++)
            assertThat(attempt(limiter, "203.0.113.1", "192.0.2." + i)).isEqualTo(401);
        assertThat(attempt(limiter, "203.0.113.1", "192.0.2.99")).isEqualTo(429);
        assertThat(new TrustedClientAddress("").resolve(request("172.30.0.10", "203.0.113.1")))
                .isEqualTo("172.30.0.10");
    }

    @Test
    void onlySingleLiteralAddressIsAcceptedFromTrustedProxy() {
        var resolver = new TrustedClientAddress("172.30.0.10");
        for (String invalid :
                new String[] {
                    "localhost",
                    "example.com",
                    "1.2.3.4, 5.6.7.8",
                    "999.1.1.1",
                    "::1%eth0",
                    " 1.2.3.4"
                })
            assertThat(resolver.resolve(request("172.30.0.10", invalid))).isEqualTo("172.30.0.10");
        var duplicate = request("172.30.0.10", "203.0.113.1");
        duplicate.addHeader("X-StockFlow-Client-IP", "203.0.113.2");
        assertThat(resolver.resolve(duplicate)).isEqualTo("172.30.0.10");
        assertThat(resolver.resolve(request("172.30.0.10", "2001:db8::1")))
                .isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThatThrownBy(() -> new TrustedClientAddress("proxy.local"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
