package com.reajason.noone.server.admin.user;

import com.reajason.noone.server.admin.user.dto.UserIpWhitelistCreateRequest;
import com.reajason.noone.server.admin.user.dto.UserIpWhitelistResponse;
import com.reajason.noone.server.api.ResourceNotFoundException;
import com.reajason.noone.server.audit.AuditAction;
import com.reajason.noone.server.audit.AuditLog;
import com.reajason.noone.server.audit.AuditModule;
import com.reajason.noone.server.util.IpUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserIpWhitelistService {

    private final UserRepository userRepository;
    private final UserIpWhitelistRepository userIpWhitelistRepository;

    @Transactional(readOnly = true)
    public List<UserIpWhitelistResponse> list(Long userId) {
        findActiveUser(userId);
        return userIpWhitelistRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @AuditLog(module = AuditModule.USER, action = AuditAction.UPDATE, targetType = "UserIpWhitelist", targetId = "#result.id", description = "'Add user IP whitelist'")
    public UserIpWhitelistResponse add(Long userId, UserIpWhitelistCreateRequest request) {
        User user = findActiveUser(userId);
        String ipAddress = normalizeIpAddress(request.getIpAddress());

        if (userIpWhitelistRepository.existsByUserIdAndIpAddress(user.getId(), ipAddress)) {
            throw duplicateIp(ipAddress);
        }

        UserIpWhitelist entry = new UserIpWhitelist();
        entry.setUser(user);
        entry.setIpAddress(ipAddress);
        try {
            return toResponse(userIpWhitelistRepository.save(entry));
        } catch (DataIntegrityViolationException ex) {
            throw duplicateIp(ipAddress);
        }
    }

    @AuditLog(module = AuditModule.USER, action = AuditAction.UPDATE, targetType = "User", targetId = "#userId", description = "'Replace user IP whitelist'")
    public List<UserIpWhitelistResponse> replace(Long userId, List<String> ipAddresses) {
        User user = findActiveUser(userId);
        List<String> normalized = normalizeAndValidateIpAddresses(ipAddresses);

        userIpWhitelistRepository.deleteByUserId(user.getId());
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<UserIpWhitelist> entries = new ArrayList<>();
        for (String ipAddress : normalized) {
            UserIpWhitelist entry = new UserIpWhitelist();
            entry.setUser(user);
            entry.setIpAddress(ipAddress);
            entries.add(entry);
        }

        return toResponses(userIpWhitelistRepository.saveAll(entries));
    }

    @AuditLog(module = AuditModule.USER, action = AuditAction.DELETE, targetType = "UserIpWhitelist", targetId = "#entryId", description = "'Delete user IP whitelist'")
    public void delete(Long userId, Long entryId) {
        findActiveUser(userId);
        UserIpWhitelist entry = userIpWhitelistRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户 IP 白名单不存在：" + entryId));
        userIpWhitelistRepository.delete(entry);
    }

    private List<UserIpWhitelistResponse> toResponses(Iterable<UserIpWhitelist> entries) {
        List<UserIpWhitelistResponse> responses = new ArrayList<>();
        for (UserIpWhitelist entry : entries) {
            responses.add(toResponse(entry));
        }
        return responses;
    }

    private UserIpWhitelistResponse toResponse(UserIpWhitelist entry) {
        UserIpWhitelistResponse response = new UserIpWhitelistResponse();
        response.setId(entry.getId());
        response.setUserId(entry.getUser().getId());
        response.setIpAddress(entry.getIpAddress());
        response.setCreatedAt(entry.getCreatedAt());
        return response;
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在：" + userId));
    }

    private List<String> normalizeAndValidateIpAddresses(List<String> ipAddresses) {
        if (ipAddresses == null || ipAddresses.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String ipAddress : ipAddresses) {
            String normalizedIp = normalizeIpAddress(ipAddress);
            normalized.add(normalizedIp);
        }
        return List.copyOf(normalized);
    }

    private String normalizeIpAddress(String ipAddress) {
        String candidate = Optional.ofNullable(ipAddress)
                .map(String::trim)
                .orElseThrow(() -> new IllegalArgumentException("IP地址不合法：null"));
        String normalized = IpUtils.normalizeExactIp(candidate);
        if (normalized == null) {
            throw new IllegalArgumentException("IP地址不合法：" + candidate);
        }
        return normalized;
    }

    private IllegalArgumentException duplicateIp(String ipAddress) {
        return new IllegalArgumentException("IP地址已存在：" + ipAddress);
    }
}
