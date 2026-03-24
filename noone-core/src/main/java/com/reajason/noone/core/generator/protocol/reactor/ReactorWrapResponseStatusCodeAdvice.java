package com.reajason.noone.core.generator.protocol.reactor;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import net.bytebuddy.asm.Advice;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;

/**
 * Applies HTTP response status code and headers in wrapResponse(ServerHttpResponse).
 */
public final class ReactorWrapResponseStatusCodeAdvice {
    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.Argument(0) ServerHttpResponse response,
            @ProtocolAdviceBindings.ResponseStatusCode int statusCode
    ) {
        try {
            Class<?> httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatusCode");
            Object httpStatusCode = httpStatusCodeClass.getMethod("valueOf", int.class).invoke(null, statusCode);
            response.getClass().getMethod("setStatusCode", httpStatusCodeClass).invoke(response, httpStatusCode);
            return;
        } catch (Throwable ignored) {
        }

        try {
            response.setStatusCode(HttpStatus.valueOf(statusCode));
        } catch (Throwable ignored) {
        }
    }
}
