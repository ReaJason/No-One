package com.reajason.noone.server.profile;

import com.reajason.noone.server.profile.config.IdentifierConfig;
import com.reajason.noone.server.profile.config.ProtocolConfig;
import com.reajason.noone.server.profile.config.ProtocolType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Profile entity.
 *
 * @author ReaJason
 * @since 2025/9/23
 */
@Entity
@Table(name = "profiles")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol_type", nullable = false)
    private ProtocolType protocolType = ProtocolType.HTTP;

    @Column(name = "identifier", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private IdentifierConfig identifier;

    @Column(name = "protocol_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private ProtocolConfig protocolConfig;

    @Column(nullable = false)
    private String password;

    @Column(name = "request_transformations", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> requestTransformations;

    @Column(name = "response_transformations", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> responseTransformations;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private LocalDateTime updatedAt;
}

