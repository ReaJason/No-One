package com.reajason.noone.server.admin.auth;

import com.reajason.noone.server.admin.user.User;
import com.reajason.noone.server.admin.user.UserIpWhitelistRepository;
import com.reajason.noone.server.admin.user.UserRepository;
import com.reajason.noone.server.admin.user.UserStatus;
import com.reajason.noone.server.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorAuthService twoFactorAuthService;
    private final JwtUtil jwtUtil; // For generating setup token
    private final UserIpWhitelistRepository ipWhitelistRepository;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_TIME_MINUTES = 30;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();
        String twoFactorCode = null;

        if (authentication instanceof TwoFactorAuthenticationToken customToken) {
            twoFactorCode = customToken.getTwoFactorCode();
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (user.getLockTime() != null) {
            if (user.getLockTime().plusMinutes(LOCK_TIME_MINUTES).isAfter(LocalDateTime.now())) {
                throw new LockedException("Account is locked. Please try again later.");
            } else {
                user.setFailedAttempts(0);
                user.setLockTime(null);
                userRepository.save(user);
            }
        }

        // IP Whitelist Check
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String ipAddress = request.getRemoteAddr();
            if (ipWhitelistRepository.existsByUserId(user.getId())
                    && !ipWhitelistRepository.existsByUserIdAndIpAddress(user.getId(), ipAddress)) {
                log.warn("Login attempt from non-whitelisted IP: {} for user: {}", ipAddress, username);
                throw new BadCredentialsException("Login not allowed from this IP address");
            }
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            int newFailCount = user.getFailedAttempts() + 1;
            user.setFailedAttempts(newFailCount);
            if (newFailCount >= MAX_FAILED_ATTEMPTS) {
                user.setLockTime(LocalDateTime.now());
                log.warn("User {} is locked out due to {} failed attempts", username, newFailCount);
            }
            userRepository.save(user);
            throw new BadCredentialsException("Invalid username or password");
        }

        // Authentication successful - reset fail attempts
        if (user.getFailedAttempts() > 0) {
            user.setFailedAttempts(0);
            user.setLockTime(null);
            userRepository.save(user);
        }

        if (user.getStatus() == UserStatus.UNACTIVATED) {
            String setupToken = jwtUtil.generateSetupToken(username);
            throw new UserNotActivatedException("User not activated", setupToken);
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new LockedException("Account is disabled");
        }

        if (user.isMfaEnabled()) {
            if (twoFactorCode == null || twoFactorCode.isBlank()) {
                throw new TwoFactorRequiredException();
            }
            if (!twoFactorAuthService.isCodeValid(user.getMfaSecret(), twoFactorCode)) {
                throw new InvalidTwoFactorCodeException();
            }
        }

        return new TwoFactorAuthenticationToken(username, password, twoFactorCode, Collections.emptyList());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return TwoFactorAuthenticationToken.class.isAssignableFrom(authentication) ||
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class
                        .isAssignableFrom(authentication);
    }
}
