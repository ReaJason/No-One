package com.reajason.noone.server.admin.plugin;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

public class PluginSpecifications {

    public static Specification<Plugin> hasName(String name) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(name)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + name.toLowerCase() + "%"
            );
        };
    }

    public static Specification<Plugin> hasLanguage(String language) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(language)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("language"), language);
        };
    }

    public static Specification<Plugin> hasType(String type) {
        return (root, query, criteriaBuilder) -> {
            if (ObjectUtils.isEmpty(type)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("type"), type);
        };
    }
}
