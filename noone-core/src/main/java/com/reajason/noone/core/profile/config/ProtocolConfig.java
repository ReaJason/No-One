package com.reajason.noone.core.profile.config;

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
        @JsonSubTypes.Type(value = WebSocketProtocolConfig.class, name = "WEBSOCKET"),
        @JsonSubTypes.Type(value = DubboProtocolConfig.class, name = "DUBBO")
})
public abstract class ProtocolConfig {

    public abstract ProtocolType getProtocolType();
}

