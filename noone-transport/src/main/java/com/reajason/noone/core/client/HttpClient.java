package com.reajason.noone.core.client;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import okhttp3.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;

/**
 * HTTP client implementation. Pure transport -- no template engine or traffic transformation.
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
    private HttpClientConfig config;

    private OkHttpClient client;

    public HttpClient(String url, HttpClientConfig config) {
        this.url = url;
        this.config = config != null ? config : HttpClientConfig.builder().build();
        this.client = buildClient();
    }

    private OkHttpClient buildClient() {
        return OkHttpSupport.build(
                config.getProxy(),
                config.getConnectTimeoutMs(),
                config.getReadTimeoutMs(),
                config.getWriteTimeoutMs(),
                config.isSkipSslVerify()
        );
    }

    @Override
    public boolean connect() {
        return true;
    }

    @Override
    public void disconnect() {
    }

    @Override
    public boolean isConnected() {
        return url != null && !url.isEmpty();
    }

    @Override
    public byte[] send(byte[] payload) {
        Request request = buildRequest(payload);
        return executeWithRetry(request);
    }

    private Request buildRequest(byte[] payload) {
        String method = config.getRequestMethod() != null
                ? config.getRequestMethod().toUpperCase()
                : "POST";
        if ("GET".equals(method)) {
            method = "POST";
        }

        Request.Builder requestBuilder = new Request.Builder();

        OkHttpSupport.applyRequestHeaders(requestBuilder, config.getRequestHeaders());
        OkHttpSupport.applyRequestCookies(requestBuilder, config.getRequestHeaders(), config.getRequestCookies());

        String ct = config.getContentType();
        MediaType mediaType = ct != null && !ct.isEmpty()
                ? MediaType.parse(ct)
                : MediaType.parse("application/octet-stream");
        RequestBody requestBody = RequestBody.create(payload, mediaType);
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
                return bytes;
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
            if (current instanceof InterruptedException) {
                return ((InterruptedException) current);
            }
            current = current.getCause();
        }
        return null;
    }
}
