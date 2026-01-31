package com.reajason.noone.server.profile.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * HTTP protocol configuration.
 *
 * @author ReaJason
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HttpProtocolConfig extends ProtocolConfig {

    @Override
    public ProtocolType getProtocolType() {
        return ProtocolType.HTTP;
    }

    private String requestMethod;

    private Map<String, String> requestHeaders;

    private HttpRequestBodyType requestBodyType;

    private String requestTemplate;

    private int responseStatusCode;

    private Map<String, String> responseHeaders;

    private HttpResponseBodyType responseBodyType;

    private String responseTemplate;
}
