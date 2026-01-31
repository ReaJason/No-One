package com.reajason.noone.server.project;

import com.reajason.noone.server.admin.user.User;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;

public class ProjectSpecifications {

    public static Specification<Project> isMember(User user) {
        return (root, query, criteriaBuilder) -> {
            Join<Project, User> members = root.join("members");
            return criteriaBuilder.equal(members.get("id"), user.getId());
        };
    }

    public static Specification<Project> hasName(String name) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(name)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<Project> hasCode(String code) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(code)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), "%" + code.toLowerCase() + "%");
        };
    }

    public static Specification<Project> hasStatus(ProjectStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    public static Specification<Project> createdAfter(LocalDateTime createdAfter) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(createdAfter)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdAfter);
        };
    }

    public static Specification<Project> createdBefore(LocalDateTime createdBefore) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(createdBefore)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdBefore);
        };
    }
}