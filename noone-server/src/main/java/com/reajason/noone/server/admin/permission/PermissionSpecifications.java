package com.reajason.noone.server.admin.permission;

import com.reajason.noone.server.admin.role.Role;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;


public class PermissionSpecifications {

    public static Specification<Permission> hasName(String name) {
        return (root, query, criteriaBuilder) ->
                StringUtils.isBlank(name) ? criteriaBuilder.conjunction() : criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("name")),
                        "%" + name.toLowerCase() + "%"
                );
    }

    public static Specification<Permission> hasRole(Long roleId) {
        return (root, query, criteriaBuilder) -> {
            if (roleId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Permission, Role> roleJoin = root.join("roles");
            Predicate predicate = criteriaBuilder.equal(roleJoin.get("id"), roleId);
            query.distinct(true);
            return predicate;
        };
    }
}
