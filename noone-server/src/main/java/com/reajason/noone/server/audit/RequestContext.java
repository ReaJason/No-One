package com.reajason.noone.server.audit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
@Getter
@Setter
public class RequestContext {
    private String ipAddress;
    private String userAgent;
    private String requestMethod;
    private String requestUri;
}
