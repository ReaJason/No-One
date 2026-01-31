package com.reajason.noone.core.generator.protocol.servlet;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import net.bytebuddy.asm.Advice;

import javax.servlet.http.HttpServletResponse;

/**
 * Applies HTTP response status code and headers in wrapResponse(HttpServletResponse).
 * Uses Object type to support both javax.servlet and jakarta.servlet APIs.
 */
public final class ServletWrapResponseStatusCodeAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.Argument(0) Object response,
            @ProtocolAdviceBindings.ResponseStatusCode int statusCode) {
        ((HttpServletResponse) response).setStatus(statusCode);
    }
}
