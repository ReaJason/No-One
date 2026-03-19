package com.reajason.noone.server.admin.user.dto;

import com.reajason.noone.server.admin.user.LoginLog;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class LoginLogQueryRequest {
    private LoginLog.LoginStatus status;
    private String ipAddress;
    private String sessionId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAfter;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime loginTimeAfter;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdBefore;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime loginTimeBefore;

    private int page = 0;
    private int pageSize = 20;
}
