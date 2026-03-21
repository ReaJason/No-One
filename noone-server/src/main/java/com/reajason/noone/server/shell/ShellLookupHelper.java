package com.reajason.noone.server.shell;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ShellLookupHelper {

    @Resource
    private ShellRepository shellRepository;

    public Shell requireById(Long id) {
        return shellRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shell not found: " + id));
    }
}
