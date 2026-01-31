package com.reajason.noone.core.generator.protocol.reactor;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import net.bytebuddy.asm.Advice;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extracts payload from reactive request body by stripping template prefix/suffix bytes.
 */
public final class ReactorGetArgFromRequestBodyAdvice {

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.Argument(0) ServerWebExchange exchange,
            @ProtocolAdviceBindings.RequestPrefixLength int start,
            @ProtocolAdviceBindings.RequestSuffixLength int suffix,
            @Advice.Return(readOnly = false) Mono<byte[]> returned
    ) {
        returned = DataBufferUtils.join(exchange.getRequest().getBody())
                .map(joined -> {
                    try {
                        byte[] body = new byte[joined.readableByteCount()];
                        joined.read(body);
                        int len = body.length - start - suffix;
                        byte[] result = new byte[len];
                        System.arraycopy(body, start, result, 0, len);
                        return result;
                    } finally {
                        DataBufferUtils.release(joined);
                    }
                })
                .switchIfEmpty(Mono.just(new byte[0]))
                .onErrorReturn(new byte[0]);
    }
}
