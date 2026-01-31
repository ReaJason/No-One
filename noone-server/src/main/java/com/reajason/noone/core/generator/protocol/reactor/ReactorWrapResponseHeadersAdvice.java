package com.reajason.noone.core.generator.protocol.reactor;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import net.bytebuddy.asm.Advice;
import org.springframework.http.server.reactive.ServerHttpResponse;

/**
 * Applies HTTP response status code and headers in wrapResponse(ServerHttpResponse).
 */
public final class ReactorWrapResponseHeadersAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.Argument(0) ServerHttpResponse response,
            @ProtocolAdviceBindings.ResponseEncodedHeaders String encodedHeaders
    ) {
        String[] parts = encodedHeaders.split("\u0000");
        for (int i = 0; i + 1 < parts.length; i += 2) {
            response.getHeaders().set(parts[i], parts[i + 1]);
        }
    }
}
