package com.reajason.noone.server.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author ReaJason
 * @since 2024/5/30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String error;

    public static ErrorResponse error(String error) {
        return new ErrorResponse(error);
    }
}
