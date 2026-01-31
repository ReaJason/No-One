package com.reajason.noone.server.profile;

import com.reajason.noone.server.profile.config.ProtocolType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

public class ProfileSpecifications {

    public static Specification<Profile> hasName(String name) {
        return (root, query, cb) -> {
            if (ObjectUtils.isEmpty(name)) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<Profile> hasProtocolType(ProtocolType protocolType) {
        return (root, query, cb) -> {
            if (protocolType == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("protocolType"), protocolType);
        };
    }

    public static Specification<Profile> hasProtocolType(String protocolType) {
        return (root, query, cb) -> {
            if (ObjectUtils.isEmpty(protocolType)) {
                return cb.conjunction();
            }
            try {
                ProtocolType type = ProtocolType.valueOf(protocolType.toUpperCase());
                return cb.equal(root.get("protocolType"), type);
            } catch (IllegalArgumentException e) {
                return cb.conjunction();
            }
        };
    }
}
