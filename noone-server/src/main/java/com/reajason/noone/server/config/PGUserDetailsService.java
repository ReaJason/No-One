package com.reajason.noone.server.config;

import com.reajason.noone.server.admin.permission.Permission;
import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserRepository;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PGUserDetailsService implements UserDetailsService {

    @Resource
    private UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .flatMap(role -> Stream.concat(
                        Stream.of(role.getName()),
                        role.getPermissions().stream().map(Permission::getName)
                ))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .accountExpired(false)
                .credentialsExpired(false)
                .accountLocked(false).build();
    }
}