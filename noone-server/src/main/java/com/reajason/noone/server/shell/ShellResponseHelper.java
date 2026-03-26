package com.reajason.noone.server.shell;

import com.reajason.noone.core.Constants;
import com.reajason.noone.core.client.CommunicationPhase;
import com.reajason.noone.core.client.ResponseDecodeException;
import com.reajason.noone.core.client.ShellCommunicationException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ShellResponseHelper {

    public Map<String, Object> handleShellConnectionResult(Map<String, Object> result) {
        if (result == null) {
            throw new ResponseDecodeException("ShellConnection returned null result");
        }
        if (result.containsKey(Constants.ERROR) && !result.containsKey(Constants.CODE)) {
            Map<String, Object> copy = new HashMap<>(result);
            copy.put(Constants.CODE, Constants.FAILURE);
            return copy;
        }
        return result;
    }

    public boolean isSuccess(Object codeObj) {
        if (!(codeObj instanceof Number code)) {
            return false;
        }
        return code.intValue() == Constants.SUCCESS;
    }

    public Map<String, Object> failureResponse(String message, Throwable e) {
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CODE, Constants.FAILURE);
        response.put(Constants.ERROR, message != null ? message : "Unknown error");
        if (e != null) {
            response.put("errorType", e.getClass().getName());
            response.put("errorMessage", safeMessage(e));
            if (e instanceof ShellCommunicationException sce) {
                response.put("phase", sce.getPhase().name());
                response.put("retriable", sce.isRetriable());
            } else {
                response.put("phase", CommunicationPhase.INTERNAL.name());
                response.put("retriable", false);
            }
        }
        return response;
    }

    public String safeMessage(Throwable t) {
        if (t == null) {
            return "Unknown error";
        }
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return msg;
    }
}
