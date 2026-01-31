package com.reajason.noone.server.admin.role;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

/**
 * 角色查询规格类
 *
 * @author ReaJason
 * @since 2025/1/27
 */
public class RoleSpecifications {

    public static Specification<Role> hasName(String name) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(name)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + name + "%"
            );
        };
    }
}
