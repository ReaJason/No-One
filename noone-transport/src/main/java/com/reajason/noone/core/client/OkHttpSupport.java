package com.reajason.noone.core.client;

import com.github.zhkl0228.impersonator.ImpersonatorApi;
import com.github.zhkl0228.impersonator.ImpersonatorFactory;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClientFactory;
import okhttp3.Request;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

public final class OkHttpSupport {

    private static final X509TrustManager TRUST_ALL_CERTIFICATES = new X509TrustManager() {
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
    };

    private OkHttpSupport() {
    }

    public static OkHttpClient build(ProxyConfig proxy, int connectTimeoutMs,
                                     int readTimeoutMs, int writeTimeoutMs, boolean skipSslVerify) {
        return build(proxy, connectTimeoutMs, readTimeoutMs, writeTimeoutMs, skipSslVerify, null, null, null, null);
    }

    public static OkHttpClient build(ProxyConfig proxy, int connectTimeoutMs,
                                     int readTimeoutMs, int writeTimeoutMs, boolean skipSslVerify,
                                     BrowserImpersonationProfile impersonationProfile,
                                     Map<String, String> explicitHeaders) {
        return build(proxy, connectTimeoutMs, readTimeoutMs, writeTimeoutMs, skipSslVerify,
                impersonationProfile, explicitHeaders, null, null);
    }

    /**
     * Builds an OkHttp client, optionally behind a browser TLS/HTTP/2 fingerprint and/or a mutual
     * TLS client certificate.
     *
     * @param impersonationProfile      the browser to impersonate, or {@code null} for the plain client
     * @param explicitHeaders           headers that must win over the ones the browser profile adds,
     *                                  usually the shell's configured custom headers
     * @param clientCertificate         a Base64 encoded PKCS#12 keystore with the client certificate
     *                                  and private key, or {@code null} for no mutual TLS
     * @param clientCertificatePassword the keystore password, may be {@code null} or empty
     */
    public static OkHttpClient build(ProxyConfig proxy, int connectTimeoutMs,
                                     int readTimeoutMs, int writeTimeoutMs, boolean skipSslVerify,
                                     BrowserImpersonationProfile impersonationProfile,
                                     Map<String, String> explicitHeaders,
                                     String clientCertificate, String clientCertificatePassword) {
        KeyManager[] keyManagers = ClientCertificateSupport.loadKeyManagers(
                clientCertificate, clientCertificatePassword);

        OkHttpClient.Builder builder = impersonationProfile == null
                ? plainBuilder(connectTimeoutMs, readTimeoutMs, writeTimeoutMs, skipSslVerify, keyManagers)
                : buildImpersonated(impersonationProfile, connectTimeoutMs, readTimeoutMs,
                        writeTimeoutMs, skipSslVerify, keyManagers, explicitHeaders).newBuilder();

        applyProxy(builder, proxy);
        return builder.build();
    }

    private static OkHttpClient.Builder plainBuilder(int connectTimeoutMs, int readTimeoutMs,
                                                     int writeTimeoutMs, boolean skipSslVerify,
                                                     KeyManager[] keyManagers) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS);

        if (skipSslVerify || keyManagers != null) {
            configureSsl(builder, skipSslVerify, keyManagers);
        }
        return builder;
    }

    private static OkHttpClient buildImpersonated(BrowserImpersonationProfile profile,
                                                  int connectTimeoutMs, int readTimeoutMs,
                                                  int writeTimeoutMs, boolean skipSslVerify,
                                                  KeyManager[] keyManagers,
                                                  Map<String, String> explicitHeaders) {
        ImpersonatorApi api = profile.newApi();
        // The ECH-capable profiles would otherwise resolve ECHConfig over DNS-over-HTTPS at
        // 223.5.5.5 on the first connection to every host. That is third-party traffic this
        // application has no reason to generate, so it is opt-out here: passing null keeps the
        // GREASE ECH a browser sends when it has no config, which is what preserves the fingerprint.
        api.setEchConfigProvider(null);

        OkHttpClientFactory factory = OkHttpClientFactory.create(api)
                .setConnectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .setReadTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .setWriteTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS);

        // Passing the trust manager here also installs the hostnameVerifier that accepts any name,
        // and keeps the certificate available to the profile's TLS stack through its KeyManagers.
        TrustManager[] trustManagers = skipSslVerify
                ? new TrustManager[]{ImpersonatorFactory.TRUST_ANY_CERTIFICATE}
                : null;
        OkHttpClient client = factory.newHttpClient(keyManagers, trustManagers, null);
        return withExplicitHeaders(client, explicitHeaders);
    }

    /**
     * Re-applies the caller's headers after the browser profile has placed its own. The profile
     * interceptor is installed by {@link OkHttpClientFactory}; an application interceptor appended
     * here runs after it, so explicitly configured values (a pinned User-Agent, for example) win
     * while everything the profile adds stays in place.
     */
    private static OkHttpClient withExplicitHeaders(OkHttpClient client, Map<String, String> explicitHeaders) {
        if (explicitHeaders == null || explicitHeaders.isEmpty()) {
            return client;
        }

        Interceptor interceptor = chain -> {
            Request.Builder requestBuilder = chain.request().newBuilder();
            applyRequestHeaders(requestBuilder, explicitHeaders);
            return chain.proceed(requestBuilder.build());
        };
        return client.newBuilder().addInterceptor(interceptor).build();
    }

    private static void applyProxy(OkHttpClient.Builder builder, ProxyConfig proxy) {
        if (proxy == null) {
            return;
        }
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

    private static void configureSsl(OkHttpClient.Builder builder, boolean skipSslVerify,
                                     KeyManager[] keyManagers) {
        try {
            X509TrustManager trustManager = skipSslVerify ? TRUST_ALL_CERTIFICATES : defaultTrustManager();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, new TrustManager[]{trustManager}, new SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
            if (skipSslVerify) {
                builder.hostnameVerifier((hostname, session) -> true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure the client SSL context", e);
        }
    }

    private static X509TrustManager defaultTrustManager() throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        // A null KeyStore means the platform's default trust store.
        factory.init((KeyStore) null);
        for (TrustManager trustManager : factory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        throw new IllegalStateException("No X509TrustManager in the platform trust store");
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
            if (cookie.getKey() == null || cookie.getKey().isEmpty()) {
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
        if (existingCookie != null && !existingCookie.isEmpty()) {
            cookieHeaderValue = existingCookie + "; " + cookieHeaderValue;
        }
        requestBuilder.header("Cookie", cookieHeaderValue);
    }
}
