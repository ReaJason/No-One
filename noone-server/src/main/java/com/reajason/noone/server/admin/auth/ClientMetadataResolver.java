package com.reajason.noone.server.admin.auth;

import org.springframework.stereotype.Component;

@Component
public class ClientMetadataResolver {

    public ClientMetadata resolve(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ClientMetadata("Unknown device", "Unknown browser", "Unknown OS");
        }

        String browser = "Unknown browser";
        if (userAgent.contains("Edg/")) {
            browser = "Edge";
        } else if (userAgent.contains("Chrome/")) {
            browser = "Chrome";
        } else if (userAgent.contains("Firefox/")) {
            browser = "Firefox";
        } else if (userAgent.contains("Safari/")) {
            browser = "Safari";
        }

        String os = "Unknown OS";
        if (userAgent.contains("Mac OS X")) {
            os = "macOS";
        } else if (userAgent.contains("Windows")) {
            os = "Windows";
        } else if (userAgent.contains("Android")) {
            os = "Android";
        } else if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iOS")) {
            os = "iOS";
        } else if (userAgent.contains("Linux")) {
            os = "Linux";
        }

        String deviceInfo;
        if (userAgent.contains("Mobile")) {
            deviceInfo = os + " mobile";
        } else if (userAgent.contains("Tablet") || userAgent.contains("iPad")) {
            deviceInfo = os + " tablet";
        } else {
            deviceInfo = os + " desktop";
        }

        return new ClientMetadata(deviceInfo, browser, os);
    }
}
