package com.nolla.bugnest.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {
    @Test
    void ignoresForwardedHeadersFromUntrustedPeers() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        assertEquals("192.0.2.10", resolver.resolve(request));
    }

    @Test
    void walksForwardedChainFromNearestTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1,10.0.0.2");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.20, 10.0.0.2");

        assertEquals("198.51.100.20", resolver.resolve(request));
    }
}
