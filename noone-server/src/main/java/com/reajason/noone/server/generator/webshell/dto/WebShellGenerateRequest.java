package com.reajason.noone.server.generator.webshell.dto;

import lombok.Data;

@Data
public class WebShellGenerateRequest {
    private Long profileId;
    private String format;
    private String servletModule;
}
