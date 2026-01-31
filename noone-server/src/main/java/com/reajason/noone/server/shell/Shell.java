package com.reajason.noone.server.shell;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Shell connection entity.
 *
 * @author ReaJason
 * @since 2025/12/27
 */
@Entity
@Table(name = "shells")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class Shell {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShellStatus status = ShellStatus.DISCONNECTED;

    @Column(name = "group_name")
    private String group;

    @Column(name = "project_id")
    private Long projectId;

    /**
     * Profile ID - required, determines request format configuration.
     */
    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    /**
     * Shell-level proxy configuration (overrides Profile default).
     * Format: "http://host:port" or "socks5://user:pass@host:port".
     */
    @Column(name = "proxy_url", length = 500)
    private String proxyUrl;

    /**
     * Shell-level custom headers (merged with Profile headers, Shell wins on conflict).
     */
    @Column(name = "custom_headers", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> customHeaders;

    /**
     * Connection timeout in milliseconds (overrides Profile default).
     */
    @Column(name = "connect_timeout_ms")
    private Integer connectTimeoutMs;

    /**
     * Read timeout in milliseconds (overrides Profile default).
     */
    @Column(name = "read_timeout_ms")
    private Integer readTimeoutMs;

    /**
     * Whether to skip SSL certificate verification.
     */
    @Column(name = "skip_ssl_verify")
    private Boolean skipSslVerify = false;

    /**
     * Maximum number of retry attempts.
     */
    @Column(name = "max_retries")
    private Integer maxRetries;

    /**
     * Delay between retries in milliseconds.
     */
    @Column(name = "retry_delay_ms")
    private Long retryDelayMs;

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "connect_time")
    private LocalDateTime connectTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}

