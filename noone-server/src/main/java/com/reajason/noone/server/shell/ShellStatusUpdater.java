package com.reajason.noone.server.shell;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class ShellStatusUpdater {

    @Resource
    private ShellRepository shellRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markConnected(Long shellId) {
        updateStatus(shellId, ShellStatus.CONNECTED, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(Long shellId) {
        updateStatus(shellId, ShellStatus.ERROR, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBasicInfo(Long shellId, Map<String, String> basicInfo) {
        try {
            Shell shell = shellRepository.findById(shellId)
                    .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + shellId));
            shell.setBasicInfo(basicInfo);
            shellRepository.save(shell);
        } catch (Exception e) {
            log.warn("Failed to update basic info: shellId={}", shellId, e);
        }
    }

    private void updateStatus(Long shellId, ShellStatus status, LocalDateTime connectTime) {
        try {
            Shell shell = shellRepository.findById(shellId)
                    .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + shellId));
            shell.setStatus(status);
            if (connectTime != null) {
                shell.setConnectTime(connectTime);
            }
            shellRepository.save(shell);
        } catch (Exception e) {
            log.warn("Failed to update shell status: shellId={}, status={}", shellId, status, e);
        }
    }
}

