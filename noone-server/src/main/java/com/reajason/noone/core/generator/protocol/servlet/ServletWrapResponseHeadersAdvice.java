package com.reajason.noone.core.generator.protocol.servlet;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import net.bytebuddy.asm.Advice;

import javax.servlet.http.HttpServletResponse;

/**
 * Applies HTTP response status code and headers in wrapResponse(HttpServletResponse).
 * Uses Object type to support both javax.servlet and jakarta.servlet APIs.
 */
public final class ServletWrapResponseHeadersAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.Argument(0) Object responseArg,
            @ProtocolAdviceBindings.ResponseEncodedHeaders String encodedHeaders
    ) {
        HttpServletResponse response = (HttpServletResponse) responseArg;
        String[] parts = encodedHeaders.split("\u0000");
        for (int i = 0; i + 1 < parts.length; i += 2) {
            response.setHeader(parts[i], parts[i + 1]);
        }
    }
}
