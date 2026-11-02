package com.securepay.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersFromDirectClients() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = requestFrom("203.0.113.7");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void usesOnlyTheSanitizedHeaderFromATrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8,192.0.2.5");
        MockHttpServletRequest request = requestFrom("10.2.3.4");
        request.addHeader("X-Forwarded-For", "198.51.100.17");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.17");
    }

    @Test
    void deniesMissingMalformedAndMultipleForwardedAddressesFromATrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest missing = requestFrom("10.2.3.4");
        MockHttpServletRequest chain = requestFrom("10.2.3.4");
        chain.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.2");
        MockHttpServletRequest hostname = requestFrom("10.2.3.4");
        hostname.addHeader("X-Forwarded-For", "client.example.com");
        MockHttpServletRequest duplicates = requestFrom("10.2.3.4");
        duplicates.addHeader("X-Forwarded-For", "198.51.100.1");
        duplicates.addHeader("X-Forwarded-For", "198.51.100.2");
        for (MockHttpServletRequest invalid : new MockHttpServletRequest[]{missing, chain, hostname, duplicates}) {
            assertThatThrownBy(() -> resolver.resolve(invalid))
                    .isInstanceOf(ClientIpResolver.InvalidForwardedAddressException.class);
        }
    }

    @Test
    void acceptsValidIpv6LiteralAndRejectsInvalidIpv4() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = requestFrom("10.0.0.3");
        request.addHeader("X-Forwarded-For", "2001:db8::1");
        assertThat(resolver.resolve(request)).contains(":");

        MockHttpServletRequest invalid = requestFrom("10.0.0.3");
        invalid.addHeader("X-Forwarded-For", "999.0.0.1");
        assertThatThrownBy(() -> resolver.resolve(invalid))
                .isInstanceOf(ClientIpResolver.InvalidForwardedAddressException.class);
    }

    @Test
    void rejectsHostnameAndEmptyEntriesInTrustedProxyConfiguration() {
        assertThatThrownBy(() -> new ClientIpResolver("proxy.internal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIpResolver("10.0.0.1,"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockHttpServletRequest requestFrom(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }
}
