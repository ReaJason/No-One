package com.reajason.noone.server.audit;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;

public class AuditLogSpecifications {

    public static Specification<AuditLogEntity> hasModule(AuditModule module) {
        return (root, query, cb) -> {
            if (module == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("module"), module);
        };
    }

    public static Specification<AuditLogEntity> hasAction(AuditAction action) {
        return (root, query, cb) -> {
            if (action == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("action"), action);
        };
    }

    public static Specification<AuditLogEntity> hasUsername(String username) {
        return (root, query, cb) -> {
            if (ObjectUtils.isEmpty(username)) {
                return cb.conjunction();
            }
            return cb.equal(root.get("username"), username);
        };
    }

    public static Specification<AuditLogEntity> hasTargetType(String targetType) {
        return (root, query, cb) -> {
            if (ObjectUtils.isEmpty(targetType)) {
                return cb.conjunction();
            }
            return cb.equal(root.get("targetType"), targetType);
        };
    }

    public static Specification<AuditLogEntity> hasTargetId(String targetId) {
        return (root, query, cb) -> {
            if (ObjectUtils.isEmpty(targetId)) {
                return cb.conjunction();
            }
            return cb.equal(root.get("targetId"), targetId);
        };
    }

    public static Specification<AuditLogEntity> isSuccess(Boolean success) {
        return (root, query, cb) -> {
            if (success == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("success"), success);
        };
    }

    public static Specification<AuditLogEntity> createdAfter(LocalDateTime after) {
        return (root, query, cb) -> {
            if (after == null) {
                return cb.conjunction();
            }
            return cb.greaterThanOrEqualTo(root.get("createdAt"), after);
        };
    }

    public static Specification<AuditLogEntity> createdBefore(LocalDateTime before) {
        return (root, query, cb) -> {
            if (before == null) {
                return cb.conjunction();
            }
            return cb.lessThanOrEqualTo(root.get("createdAt"), before);
        };
    }

    public static Specification<AuditLogEntity> hasUserId(Long userId) {
        return (root, query, cb) -> {
            if (userId == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("userId"), userId);
        };
    }
}
