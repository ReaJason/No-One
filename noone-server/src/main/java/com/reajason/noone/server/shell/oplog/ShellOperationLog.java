package com.reajason.noone.server.shell.oplog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "shell_operation_logs", indexes = {
        @Index(name = "idx_shell_op_log_shell_user", columnList = "shell_id, username"),
        @Index(name = "idx_shell_op_log_created_at", columnList = "created_at")
})
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class ShellOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shell_id", nullable = false)
    private Long shellId;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShellOperationType operation;

    @Column(name = "plugin_id")
    private String pluginId;

    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> args;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    private boolean success;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
