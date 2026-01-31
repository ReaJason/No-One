package com.reajason.noone.server.profile.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base protocol configuration with Jackson polymorphic serialization.
 *
 * @author ReaJason
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = HttpProtocolConfig.class, name = "HTTP"),
        @JsonSubTypes.Type(value = WebSocketProtocolConfig.class, name = "WEBSOCKET")
})
public abstract class ProtocolConfig {

    public abstract ProtocolType getProtocolType();
}

