package com.reajason.noone.server.generator.webshell.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebShellGenerateResponse {
    private String content;
    private String format;
    private String fileName;
}
