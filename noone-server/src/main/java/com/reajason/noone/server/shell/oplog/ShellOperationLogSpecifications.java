package com.reajason.noone.server.shell.oplog;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

public class ShellOperationLogSpecifications {

    public static Specification<ShellOperationLog> hasShellId(Long shellId) {
        return (root, query, cb) -> {
            if (shellId == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("shellId"), shellId);
        };
    }

    public static Specification<ShellOperationLog> hasUsername(String username) {
        return (root, query, cb) -> {
            if (ObjectUtils.isEmpty(username)) {
                return cb.conjunction();
            }
            return cb.equal(root.get("username"), username);
        };
    }

    public static Specification<ShellOperationLog> hasPluginId(String pluginId) {
        return (root, query, cb) -> {
            if (ObjectUtils.isEmpty(pluginId)) {
                return cb.conjunction();
            }
            return cb.equal(root.get("pluginId"), pluginId);
        };
    }

    public static Specification<ShellOperationLog> hasOperation(ShellOperationType operation) {
        return (root, query, cb) -> {
            if (operation == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("operation"), operation);
        };
    }

    public static Specification<ShellOperationLog> isSuccess(Boolean success) {
        return (root, query, cb) -> {
            if (success == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("success"), success);
        };
    }
}
