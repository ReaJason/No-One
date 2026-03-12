package com.reajason.noone.server.admin.user;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_logs")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String username;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "browser")
    private String browser;

    @Column(name = "os")
    private String os;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginStatus status;

    @Column(name = "fail_reason")
    private String failReason;

    @CreatedDate
    @Column(name = "login_time", nullable = false, updatable = false)
    private LocalDateTime loginTime;

    public enum LoginStatus {
        SUCCESS,
        INVALID_CREDENTIALS,
        REQUIRE_2FA,
        REQUIRE_SETUP,
        REQUIRE_PASSWORD_CHANGE,
        LOCKED,
        DISABLED
    }
}
