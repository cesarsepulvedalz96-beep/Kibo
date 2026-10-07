package com.stockflow.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Only an explicitly trusted immediate proxy may supply the dedicated client address header. */
public final class TrustedClientAddress {
    private final Set<String> proxies;

    public TrustedClientAddress(String trustedProxies) {
        proxies =
                Arrays.stream(trustedProxies.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(
                                value -> {
                                    String normalized = literal(value);
                                    if (normalized == null)
                                        throw new IllegalArgumentException(
                                                "Trusted proxies must be literal IP addresses");
                                    return normalized;
                                })
                        .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remote = literal(request.getRemoteAddr());
        if (remote == null || !proxies.contains(remote)) return request.getRemoteAddr();
        var headers = request.getHeaders("X-StockFlow-Client-IP");
        if (headers == null || !headers.hasMoreElements()) return remote;
        String candidate = literal(headers.nextElement());
        return candidate == null || headers.hasMoreElements() ? remote : candidate;
    }

    private static String literal(String value) {
        if (value == null || value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")) return null;
        try {
            return InetAddress.ofLiteral(value).getHostAddress();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
