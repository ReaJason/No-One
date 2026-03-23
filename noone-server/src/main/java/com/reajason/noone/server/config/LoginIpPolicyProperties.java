package com.reajason.noone.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "noone.security")
public class LoginIpPolicyProperties {
    private List<String> loginIpWhitelist = new ArrayList<>();
}
