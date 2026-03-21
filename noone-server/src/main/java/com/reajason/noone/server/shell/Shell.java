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

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column
    private Boolean staging = false;

    @Column(name = "shell_type")
    private String shellType;

    /**
     * Dubbo service interface name for RPC invocation (only used with DUBBO protocol).
     */
    @Column(name = "interface_name", length = 500)
    private String interfaceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private ShellLanguage language = ShellLanguage.JAVA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShellStatus status = ShellStatus.DISCONNECTED;

    @Column(length = 500)
    private String tags;

    @Column(name = "project_id")
    private Long projectId;


    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "loader_profile_id")
    private Long loaderProfileId;

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

    /**
     * Normalized system info collected from the system-info plugin.
     * Keys: os, arch, runtimeType, runtimeVersion.
     */
    @Column(name = "basic_info", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> basicInfo;

    @Column(name = "os")
    private String os;

    @Column(name = "arch")
    private String arch;

    @Column(name = "runtime_version")
    private String runtimeVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_online_at")
    private LocalDateTime lastOnlineAt;

    @Column(name = "last_operator_id")
    private Long lastOperatorId;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(length = 2000)
    private String remark;

    @Column
    private boolean deleted;
}
