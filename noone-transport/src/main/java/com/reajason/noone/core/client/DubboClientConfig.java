package com.reajason.noone.core.client;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration specific to Dubbo RPC invocation.
 *
 * @author ReaJason
 */
@Data
@Builder
public class DubboClientConfig {

    /**
     * Fully-qualified Dubbo service interface name, e.g. "com.example.ShellService".
     * Used for ReferenceConfig.setInterface().
     */
    private String interfaceName;

    /**
     * Remote method name to invoke via GenericService.$invoke.
     */
    @Builder.Default
    private String methodName = "handle";

    /**
     * Parameter type descriptors passed to GenericService.$invoke.
     * Defaults to a single byte[] parameter.
     */
    @Builder.Default
    private String[] parameterTypes = new String[]{"[B"};

    @Builder.Default
    private int readTimeoutMs = 60000;
}
