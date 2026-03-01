package com.reajason.noone.server.shell.oplog;

import com.reajason.noone.server.shell.oplog.dto.ShellOperationLogResponse;
import org.springframework.stereotype.Component;

@Component
public class ShellOperationLogMapper {

    public ShellOperationLogResponse toResponse(ShellOperationLog log) {
        ShellOperationLogResponse response = new ShellOperationLogResponse();
        response.setId(log.getId());
        response.setShellId(log.getShellId());
        response.setUsername(log.getUsername());
        response.setOperation(log.getOperation().name());
        response.setPluginId(log.getPluginId());
        response.setAction(log.getAction());
        response.setArgs(log.getArgs());
        response.setResult(log.getResult());
        response.setSuccess(log.isSuccess());
        response.setErrorMessage(log.getErrorMessage());
        response.setDurationMs(log.getDurationMs());
        response.setCreatedAt(log.getCreatedAt());
        return response;
    }
}
