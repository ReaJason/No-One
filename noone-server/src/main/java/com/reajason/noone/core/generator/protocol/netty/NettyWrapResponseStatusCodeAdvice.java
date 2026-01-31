package com.reajason.noone.core.generator.protocol.netty;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.bytebuddy.asm.Advice;

/**
 * Applies HTTP response status code and headers in wrapResponse(Object) for Netty HttpResponse.
 */
public final class NettyWrapResponseStatusCodeAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.Argument(0) Object response,
            @ProtocolAdviceBindings.ResponseStatusCode int statusCode) {
        ((FullHttpResponse) response).setStatus(HttpResponseStatus.valueOf(statusCode));
    }
}
