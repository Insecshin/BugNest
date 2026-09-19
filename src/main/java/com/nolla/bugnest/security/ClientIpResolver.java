package com.nolla.bugnest.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientIpResolver {
    private final Set<String> trustedProxies;

    public ClientIpResolver(@Value("${bugnest.security.rate-limit.trusted-proxies:}") String configuredProxies) {
        trustedProxies = Arrays.stream(configuredProxies.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(ClientIpResolver::normalizeIp)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeIp(request.getRemoteAddr());
        if (!trustedProxies.contains(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }

        String[] chain = forwardedFor.split(",");
        for (int i = chain.length - 1; i >= 0; i--) {
            String candidate;
            try {
                candidate = normalizeIp(chain[i].strip());
            } catch (IllegalArgumentException exception) {
                return remoteAddress;
            }
            if (!trustedProxies.contains(candidate)) {
                return candidate;
            }
        }
        return remoteAddress;
    }

    private static String normalizeIp(String value) {
        if (value == null || !value.matches("[0-9a-fA-F:.]+")) {
            throw new IllegalArgumentException("Invalid IP address");
        }
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid IP address", exception);
        }
    }
}
