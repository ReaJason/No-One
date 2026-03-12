package com.reajason.noone.server.config;

import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserAuthorityResolver;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.admin.user.UserStatus;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PGUserDetailsService implements UserDetailsService {

    @Resource
    private UserRepository userRepository;

    @Resource
    private UserAuthorityResolver userAuthorityResolver;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        Set<GrantedAuthority> authorities = userAuthorityResolver.resolveGrantedAuthorities(user);
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .disabled(UserStatus.DISABLED.equals(user.getStatus()) || UserStatus.UNACTIVATED.equals(user.getStatus()))
                .accountExpired(false)
                .credentialsExpired(false)
                .accountLocked(UserStatus.LOCKED.equals(user.getStatus()))
                .build();
    }
}
