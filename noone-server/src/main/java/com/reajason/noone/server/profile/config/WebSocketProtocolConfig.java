package com.reajason.noone.server.profile.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * WebSocket protocol configuration.
 *
 * @author ReaJason
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSocketProtocolConfig extends ProtocolConfig {

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.WEBSOCKET;
    }

    /**
     * HTTP headers used during the WebSocket handshake.
     */
    private Map<String, String> handshakeHeaders;

    private String messageTemplate;

    private String responseTemplate;

    private MessageFormat messageFormat = MessageFormat.TEXT;
}

