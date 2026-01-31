package com.reajason.noone.core.client;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Map;

/**
 * Client configuration for HTTP/WebSocket connections.
 * This is a simplified DTO for noone-core (no JPA dependencies).
 *
 * @author ReaJason
 */
@Data
@Builder
public class ClientConfig {

    /**
     * HTTP request method (GET, POST, etc.)
     */
    private String requestMethod;

    /**
     * Custom headers to include in requests.
     */
    private Map<String, String> requestHeaders;

    /**
     * Query parameters to include in request URL.
     */
    private Map<String, String> requestParams;

    /**
     * Cookies to include in request (serialized to the "Cookie" header).
     */
    private Map<String, String> requestCookies;

    /**
     * Request body template (for non-form requests)
     */
    private String requestTemplate;

    /**
     * Request body type when payload is placed in body.
     */
    @Builder.Default
    private HttpRequestBodyType requestBodyType = HttpRequestBodyType.FORM_URLENCODED;

    /**
     * Expected HTTP response status code (optional). When set (>0), only this code is treated as success.
     */
    private Integer expectedResponseStatusCode;

    /**
     * Response body template used to extract payload from response body.
     */
    private String responseTemplate;

    /**
     * Response body type used to decode/extract payload.
     */
    @Builder.Default
    private HttpResponseBodyType responseBodyType = HttpResponseBodyType.TEXT;

    /**
     * Password seed for traffic encryption/decryption.
     * This value is used only for deriving deterministic keys (no plaintext recovery).
     */
    @ToString.Exclude
    private String transformerPassword;

    /**
     * Transformers applied to client outbound request payload.
     */
    private List<String> requestTransformations;

    /**
     * Transformers applied to client inbound response payload.
     */
    private List<String> responseTransformations;

    /**
     * Proxy configuration
     */
    private ProxyConfig proxy;

    /**
     * Connection timeout in milliseconds
     */
    @Builder.Default
    private int connectTimeoutMs = 30000;

    /**
     * Read timeout in milliseconds
     */
    @Builder.Default
    private int readTimeoutMs = 60000;

    /**
     * Write timeout in milliseconds
     */
    @Builder.Default
    private int writeTimeoutMs = 60000;

    /**
     * Skip SSL certificate verification
     */
    @Builder.Default
    private boolean skipSslVerify = false;

    /**
     * Maximum number of retry attempts
     */
    @Builder.Default
    private int maxRetries = 0;

    /**
     * Delay between retries in milliseconds
     */
    @Builder.Default
    private long retryDelayMs = 1000;

    /**
     * Use exponential backoff for retries
     */
    @Builder.Default
    private boolean exponentialBackoff = true;

    /**
     * Proxy configuration
     */
    @Data
    @Builder
    public static class ProxyConfig {
        /**
         * Proxy type: HTTP, SOCKS4, SOCKS5
         */
        private String type;

        /**
         * Proxy host
         */
        private String host;

        /**
         * Proxy port
         */
        private int port;

        /**
         * Proxy username (optional)
         */
        private String username;

        /**
         * Proxy password (optional)
         */
        private String password;

        /**
         * Convert to Java Proxy object
         */
        public Proxy toJavaProxy() {
            Proxy.Type proxyType = "SOCKS5".equalsIgnoreCase(type) || "SOCKS4".equalsIgnoreCase(type)
                    ? Proxy.Type.SOCKS
                    : Proxy.Type.HTTP;
            return new Proxy(proxyType, new InetSocketAddress(host, port));
        }

        /**
         * Check if proxy authentication is required
         */
        public boolean hasAuth() {
            return username != null && !username.isEmpty();
        }
    }
}
