package com.reajason.noone.core.client;

import com.reajason.noone.core.exception.RequestInterruptedException;
import com.reajason.noone.core.exception.RequestSendException;
import com.reajason.noone.core.exception.RequestSerializeException;
import com.reajason.noone.core.exception.ResponseDecodeException;
import com.reajason.noone.core.exception.ResponseStatusException;
import com.reajason.noone.core.exception.ShellRequestException;
import com.reajason.noone.core.profile.config.HttpRequestBodyType;
import com.reajason.noone.core.transform.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import okhttp3.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client implementation with configurable proxy, headers, timeout, and SSL settings.
 *
 * @author ReaJason
 * @since 2025/12/13
 */
@NoArgsConstructor
public class HttpClient implements Client {

    @Setter
    @Getter
    private String url;

    @Setter
    @Getter
    private ClientConfig config;

    private OkHttpClient client;

    public HttpClient(String url) {
        this.url = url;
        this.config = ClientConfig.builder().build();
        this.client = buildClient();
    }

    public HttpClient(String url, ClientConfig config) {
        this.url = url;
        this.config = config != null ? config : ClientConfig.builder().build();
        this.client = buildClient();
    }

    private OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getWriteTimeoutMs(), TimeUnit.MILLISECONDS);

        // Configure proxy
        if (config.getProxy() != null) {
            ClientConfig.ProxyConfig proxyConfig = config.getProxy();
            builder.proxy(proxyConfig.toJavaProxy());

            // Proxy authentication
            if (proxyConfig.hasAuth()) {
                builder.proxyAuthenticator((route, response) -> {
                    String credential = Credentials.basic(
                            proxyConfig.getUsername(),
                            proxyConfig.getPassword()
                    );
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                });
            }
        }

        // Skip SSL verification if configured
        if (config.isSkipSslVerify()) {
            configureInsecureSsl(builder);
        }

        return builder.build();
    }

    private void configureInsecureSsl(OkHttpClient.Builder builder) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure insecure SSL", e);
        }
    }

    @Override
    public boolean connect() {
        // HTTP is stateless, no explicit connection needed
        return true;
    }

    @Override
    public void disconnect() {
        // HTTP is stateless, no explicit disconnection needed
    }

    @Override
    public boolean isConnected() {
        return url != null && !url.isEmpty();
    }

    @Override
    public byte[] send(String payload) {
        return send(payload != null ? payload.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    @Override
    public byte[] send(byte[] payload) {
        TransformationSpec requestSpec;
        byte[] transformedRequest;
        Request request;
        try {
            requestSpec = TransformationSpec.parse(config.getRequestTransformations());
            transformedRequest = TrafficTransformer.outbound(
                    payload,
                    requestSpec,
                    config.getTransformerPassword()
            );
            request = buildRequest(transformedRequest, requestSpec);
        } catch (RuntimeException e) {
            throw new RequestSerializeException("Failed to prepare HTTP request payload", e);
        }

        byte[] extractedResponsePayload = executeWithRetry(request);
        TransformationSpec responseSpec = TransformationSpec.parse(config.getResponseTransformations());
        byte[] inbound;
        try {
            inbound = TrafficTransformer.inbound(
                    extractedResponsePayload,
                    responseSpec,
                    config.getTransformerPassword()
            );
        } catch (RuntimeException e) {
            throw new ResponseDecodeException("Failed to decode response payload", e);
        }
        if (inbound == null) {
            throw new ResponseDecodeException("Decoded response payload is null");
        }
        return inbound;
    }

    private Request buildRequest(byte[] payload, TransformationSpec requestSpec) {
        String method = config.getRequestMethod() != null
                ? config.getRequestMethod().toUpperCase()
                : "POST";
        if ("GET".equals(method)) {
            method = "POST";
        }

        Request.Builder requestBuilder = new Request.Builder();

        applyRequestHeaders(requestBuilder);
        applyRequestCookies(requestBuilder);

        RequestBody requestBody = buildRequestBody(payload, requestSpec);
        requestBuilder.url(buildRequestUrl()).method(method, requestBody);

        return requestBuilder.build();
    }

    private HttpUrl buildRequestUrl() {
        Map<String, String> params = config.getRequestParams();
        if (params == null || params.isEmpty()) {
            return HttpUrl.get(url);
        }

        HttpUrl parsed = HttpUrl.get(url);
        HttpUrl.Builder urlBuilder = parsed.newBuilder();
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (param.getKey() == null) {
                continue;
            }
            urlBuilder.setQueryParameter(param.getKey(), param.getValue());
        }
        return urlBuilder.build();
    }

    private void applyRequestHeaders(Request.Builder requestBuilder) {
        Map<String, String> headers = config.getRequestHeaders();
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getKey() == null) {
                continue;
            }
            requestBuilder.header(header.getKey(), header.getValue());
        }
    }

    private void applyRequestCookies(Request.Builder requestBuilder) {
        Map<String, String> cookies = config.getRequestCookies();
        if (cookies == null || cookies.isEmpty()) {
            return;
        }

        StringJoiner joiner = new StringJoiner("; ");
        for (Map.Entry<String, String> cookie : cookies.entrySet()) {
            if (cookie.getKey() == null || cookie.getKey().isBlank()) {
                continue;
            }
            String value = cookie.getValue() != null ? cookie.getValue() : "";
            joiner.add(cookie.getKey() + "=" + value);
        }

        String cookieHeaderValue = joiner.toString();
        if (cookieHeaderValue.isEmpty()) {
            return;
        }

        String existingCookie = null;
        Map<String, String> headers = config.getRequestHeaders();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey() != null && "Cookie".equalsIgnoreCase(header.getKey())) {
                    existingCookie = header.getValue();
                    break;
                }
            }
        }
        if (existingCookie != null && !existingCookie.isBlank()) {
            cookieHeaderValue = existingCookie + "; " + cookieHeaderValue;
        }
        requestBuilder.header("Cookie", cookieHeaderValue);
    }

    private RequestBody buildRequestBody(byte[] payload, TransformationSpec requestSpec) {
        HttpRequestBodyType bodyType = config.getRequestBodyType() != null
                ? config.getRequestBodyType()
                : HttpRequestBodyType.FORM_URLENCODED;
        String template = config.getRequestTemplate();

        if (requestSpec != null
                && requestSpec.encoding() == EncodingAlgorithm.NONE
                && (requestSpec.compression() != CompressionAlgorithm.NONE
                || requestSpec.encryption() != EncryptionAlgorithm.NONE)
                && bodyType != HttpRequestBodyType.BINARY
                && bodyType != HttpRequestBodyType.MULTIPART_FORM_DATA) {
            throw new IllegalStateException("Encoding NONE requires BINARY or MULTIPART_FORM_DATA when compression/encryption is enabled");
        }

        HttpBodyTemplateEngine.EncodedBody encoded = HttpBodyTemplateEngine.encodeRequestBody(
                bodyType,
                template,
                payload
        );

        String contentType = encoded.contentType();

        MediaType mediaType = contentType != null && !contentType.isBlank()
                ? MediaType.parse(contentType)
                : null;
        return RequestBody.create(encoded.bytes(), mediaType);
    }

    private byte[] executeWithRetry(Request request) {
        int attempts = 0;
        int maxAttempts = config.getMaxRetries() + 1;
        long delay = config.getRetryDelayMs();

        while (attempts < maxAttempts) {
            try (Response response = client.newCall(request).execute()) {
                Integer expectedResponseStatusCode = config.getExpectedResponseStatusCode();
                int code = response.code();
                if (expectedResponseStatusCode != null
                        && expectedResponseStatusCode > 0
                        && expectedResponseStatusCode != code) {
                    throw new ResponseStatusException(expectedResponseStatusCode, code);
                }
                ResponseBody body = response.body();
                byte[] bytes = body.bytes();
                if (bytes == null) {
                    throw new ResponseDecodeException("HTTP response body bytes are null, status: " + code);
                }
                byte[] responseDataBytes = HttpBodyTemplateEngine.extractResponsePayloadBytes(
                        config.getResponseBodyType(),
                        config.getResponseTemplate(),
                        bytes
                );
                if (responseDataBytes == null) {
                    throw new ResponseDecodeException("Failed to extract payload from HTTP response body, status: " + code + ", body: " + new String(bytes));
                }
                return responseDataBytes;
            } catch (ResponseStatusException | ResponseDecodeException e) {
                throw e;
            } catch (IOException e) {
                if (isInterruptedFailure(e)) {
                    throw interruptedRequest(e, "HTTP request was interrupted");
                }
                attempts++;
                if (attempts >= maxAttempts) {
                    throw new RequestSendException("HTTP request failed after " + attempts + " attempt(s), due to " + e.getMessage(), attempts, e);
                }
            } catch (Exception e) {
                if (isInterruptedFailure(e)) {
                    throw interruptedRequest(e, "HTTP request was interrupted");
                }
                throw new ShellRequestException("Unexpected HTTP request execution failure", false, e);
            }

            try {
                Thread.sleep(delay);
                if (config.isExponentialBackoff()) {
                    delay *= 2;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RequestInterruptedException("HTTP retry was interrupted", ie);
            }
        }
        throw new RequestSendException("HTTP request failed with unknown transport error", attempts, null);
    }

    private RequestInterruptedException interruptedRequest(Throwable throwable, String message) {
        InterruptedException interruptedException = findInterruptedException(throwable);
        Thread.currentThread().interrupt();
        return new RequestInterruptedException(message, interruptedException != null ? interruptedException : throwable);
    }

    private boolean isInterruptedFailure(Throwable throwable) {
        return findInterruptedException(throwable) != null || throwable instanceof InterruptedIOException;
    }

    private InterruptedException findInterruptedException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException interruptedException) {
                return interruptedException;
            }
            current = current.getCause();
        }
        return null;
    }

}
