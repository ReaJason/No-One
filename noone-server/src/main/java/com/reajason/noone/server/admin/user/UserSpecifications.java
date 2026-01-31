package com.reajason.noone.server.admin.user;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;

/**
 * 用户查询规格类
 *
 * @author ReaJason
 * @since 2025/1/27
 */
public class UserSpecifications {

    public static Specification<User> hasUsername(String username) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(username)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("username")),
                    "%" + username.toLowerCase() + "%"
            );
        };
    }

    public static Specification<User> hasRole(Long roleId) {
        return (root, query, criteriaBuilder) -> {
            if (roleId == null || roleId == 0L) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(
                    root.join("roles").get("id"),
                    roleId
            );
        };
    }

    public static Specification<User> isEnabled(Boolean enabled) {
        return (root, query, criteriaBuilder) -> {
            if (enabled == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("enabled"), enabled);
        };
    }

    public static Specification<User> createdAfter(LocalDateTime createdAfter) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(createdAfter)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdAfter);
        };
    }

    public static Specification<User> createdBefore(LocalDateTime createdBefore) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(createdBefore)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdBefore);
        };
    }
}
