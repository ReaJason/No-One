package com.reajason.noone.core.generator;

import lombok.Getter;

@Getter
public enum ServletModule {
    JAVAX("javax.servlet"),
    JAKARTA("jakarta.servlet");

    private final String basePackage;

    ServletModule(String basePackage) {
        this.basePackage = basePackage;
    }

    public String httpServletRequest() {
        return basePackage + ".http.HttpServletRequest";
    }

    public String httpServletResponse() {
        return basePackage + ".http.HttpServletResponse";
    }

    public String cookie() {
        return basePackage + ".http.Cookie";
    }
}
