package com.reajason.noone.core.profile.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Dubbo protocol configuration for RPC-based shell communication.
 *
 * @author ReaJason
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DubboProtocolConfig extends ProtocolConfig {

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.DUBBO;
    }

    /**
     * Remote method name to invoke via GenericService.$invoke.
     */
    private String methodName;

    /**
     * Parameter type descriptors passed to GenericService.$invoke.
     */
    private String[] parameterTypes;

    /**
     * Template for encoding the outbound payload before Dubbo invocation.
     */
    private String requestTemplate;

    /**
     * Template for extracting the payload from the Dubbo response.
     */
    private String responseTemplate;
}
