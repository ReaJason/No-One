package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.role.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class UserAuthorityResolver {

    public Set<String> resolveAuthorityCodes(User user) {
        if (user == null || user.getRoles() == null) {
            return Collections.emptySet();
        }

        return user.getRoles().stream()
                .flatMap(this::expandRoleAuthorities)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<GrantedAuthority> resolveGrantedAuthorities(User user) {
        return resolveAuthorityCodes(user).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Stream<String> expandRoleAuthorities(Role role) {
        if (role == null) {
            return Stream.empty();
        }

        Stream<String> roleAuthority = role.getName() == null
                ? Stream.empty()
                : Stream.of("ROLE_" + role.getName().toUpperCase());
        Stream<String> permissionAuthorities = role.getPermissions() == null
                ? Stream.empty()
                : role.getPermissions().stream()
                .map(Permission::getCode)
                .filter(code -> code != null && !code.isBlank());

        return Stream.concat(roleAuthority, permissionAuthorities);
    }
}
