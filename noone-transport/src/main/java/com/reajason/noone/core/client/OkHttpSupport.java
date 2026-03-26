package com.reajason.noone.core.client;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

public final class OkHttpSupport {

    private OkHttpSupport() {
    }

    public static OkHttpClient build(ProxyConfig proxy, int connectTimeoutMs,
                                     int readTimeoutMs, int writeTimeoutMs, boolean skipSslVerify) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS);

        if (proxy != null) {
            builder.proxy(proxy.toJavaProxy());
            if (proxy.hasAuth()) {
                builder.proxyAuthenticator((route, response) -> {
                    String credential = Credentials.basic(proxy.getUsername(), proxy.getPassword());
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                });
            }
        }

        if (skipSslVerify) {
            configureInsecureSsl(builder);
        }

        return builder.build();
    }

    private static void configureInsecureSsl(OkHttpClient.Builder builder) {
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

    public static void applyRequestHeaders(Request.Builder requestBuilder, Map<String, String> headers) {
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

    public static void applyRequestCookies(Request.Builder requestBuilder, Map<String, String> headers, Map<String, String> cookies) {
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
}
