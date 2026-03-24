package com.reajason.noone.server.profile;

import com.reajason.noone.core.profile.config.ProtocolType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

public class ProfileSpecifications {

    public static Specification<ProfileEntity> notDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.isFalse(root.get("deleted")),
                criteriaBuilder.isNull(root.get("deleted"))
        );
    }

    public static Specification<ProfileEntity> hasName(String name) {
        return (root, query, cb) -> {
            if (ObjectUtils.isEmpty(name)) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<ProfileEntity> hasProtocolType(String protocolType) {
        return (root, query, cb) -> {
            if (ObjectUtils.isEmpty(protocolType)) {
                return cb.conjunction();
            }
            ProtocolType type = ProtocolType.valueOf(protocolType.toUpperCase());
            return cb.equal(root.get("protocolType"), type);
        };
    }
}
