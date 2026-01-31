package com.reajason.noone.server.profile.config;

import lombok.Data;

/**
 * Retry configuration for HTTP requests.
 *
 * @author ReaJason
 */
@Data
public class RetryConfig {

    private int maxRetries = 3;

    private long retryDelayMs = 1000;

    private boolean exponentialBackoff = true;
}

