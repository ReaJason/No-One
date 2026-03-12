package com.reajason.noone.server.admin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SensitiveActionChallengeResponse {
    private String challengeToken;
    private LocalDateTime expiresAt;
}
