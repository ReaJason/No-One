package com.reajason.noone.core.generator.protocol.netty;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import io.netty.handler.codec.http.FullHttpResponse;
import net.bytebuddy.asm.Advice;

/**
 * Applies HTTP response status code and headers in wrapResponse(Object) for Netty HttpResponse.
 */
public final class NettyWrapResponseHeadersAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.Argument(0) Object response,
            @ProtocolAdviceBindings.ResponseEncodedHeaders String encodedHeaders) {
        String[] parts = encodedHeaders.split("\u0000");
        for (int i = 0; i + 1 < parts.length; i += 2) {
            ((FullHttpResponse) response).headers().set(parts[i], parts[i + 1]);
        }
    }
}
