package com.reajason.noone.core.generator.protocol.reactor;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import net.bytebuddy.asm.Advice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extracts payload from x-www-form-urlencoded request parameter.
 *
 * <p>Tries query parameter first (parity with servlet getParameter), then form data.</p>
 */
public final class ReactorGetArgFromRequestFormUrlencodedAdvice {

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.Argument(0) ServerWebExchange exchange,
            @ProtocolAdviceBindings.RequestParameterName String parameterName,
            @ProtocolAdviceBindings.RequestPrefixLength int start,
            @ProtocolAdviceBindings.RequestSuffixLength int suffix,
            @Advice.Return(readOnly = false) Mono<byte[]> returned
    ) {
        returned = exchange.getFormData()
                .flatMap(formData -> Mono.fromCallable(() -> {
                    String payload = formData.getFirst(parameterName);
                    return payload.substring(start, payload.length() - suffix).getBytes("UTF-8");
                }))
                .switchIfEmpty(Mono.just(new byte[0]))
                .onErrorReturn(new byte[0]);
    }
}
