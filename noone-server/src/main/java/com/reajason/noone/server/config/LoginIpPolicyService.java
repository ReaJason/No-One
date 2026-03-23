package com.reajason.noone.server.config;

import com.reajason.noone.server.util.IpUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class LoginIpPolicyService {

    private final Set<String> loginIpWhitelist;

    public LoginIpPolicyService(LoginIpPolicyProperties properties) {
        this.loginIpWhitelist = normalizeWhitelist(properties);
    }

    public boolean isAllowed(String ipAddress) {
        if (loginIpWhitelist.isEmpty()) {
            return true;
        }

        String normalizedIpAddress = IpUtils.normalizeExactIp(ipAddress);
        String candidate = normalizedIpAddress == null ? ipAddress : normalizedIpAddress;
        return loginIpWhitelist.contains(candidate);
    }

    private Set<String> normalizeWhitelist(LoginIpPolicyProperties properties) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String ipAddress : properties.getLoginIpWhitelist()) {
            String normalizedIp = IpUtils.normalizeExactIp(ipAddress);
            if (normalizedIp == null) {
                throw new IllegalArgumentException("IP地址不合法：" + ipAddress);
            }
            normalized.add(normalizedIp);
        }
        return Set.copyOf(normalized);
    }
}
