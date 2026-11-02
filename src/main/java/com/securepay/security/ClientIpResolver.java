package com.securepay.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Resolves the rate-limit client address without trusting caller-controlled proxy headers.
 * The trusted edge proxy MUST overwrite X-Forwarded-For with exactly one numeric client IP.
 */
@Component
final class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private final List<IpAddressMatcher> trustedProxies;

    ClientIpResolver(@Value("${securepay.rate-limit.trusted-proxies:}") String trustedProxyCidrs) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        if (trustedProxyCidrs != null && !trustedProxyCidrs.isBlank()) {
            for (String configured : trustedProxyCidrs.split(",", -1)) {
                String cidr = configured.trim();
                String addressPart = cidr.split("/", 2)[0];
                if (cidr.isEmpty() || numericAddress(addressPart) == null) {
                    throw new IllegalArgumentException("Trusted proxies must be numeric IP addresses/CIDRs");
                }
                matchers.add(new IpAddressMatcher(cidr));
            }
        }
        this.trustedProxies = List.copyOf(matchers);
    }

    String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null || remote.isBlank()) {
            return "unknown";
        }
        boolean trusted = trustedProxies.stream().anyMatch(matcher -> matcher.matches(remote));
        if (!trusted) {
            // Never consider forwarded headers from direct callers or untrusted intermediaries.
            return remote;
        }

        Enumeration<String> values = request.getHeaders(FORWARDED_FOR);
        if (values == null || !values.hasMoreElements()) {
            throw new InvalidForwardedAddressException();
        }
        String header = values.nextElement();
        if (values.hasMoreElements() || header == null || header.contains(",")) {
            throw new InvalidForwardedAddressException();
        }
        String canonical = numericAddress(header.trim());
        if (canonical == null) {
            throw new InvalidForwardedAddressException();
        }
        return canonical;
    }

    /** Validate numeric literals only. Never resolve an untrusted hostname through DNS. */
    private static String numericAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        if (address.indexOf(':') >= 0) {
            if (!address.matches("[0-9a-fA-F:]+")) {
                return null;
            }
            try {
                InetAddress parsed = InetAddress.getByName(address);
                return parsed instanceof Inet6Address ? parsed.getHostAddress() : null;
            } catch (UnknownHostException ex) {
                return null;
            }
        }

        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (!part.matches("(0|[1-9][0-9]{0,2})")) {
                return null;
            }
            octets[i] = Integer.parseInt(part);
            if (octets[i] > 255) {
                return null;
            }
        }
        return octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
    }

    static final class InvalidForwardedAddressException extends RuntimeException {
        InvalidForwardedAddressException() {
            super("Invalid client address from trusted proxy");
        }
    }
}
