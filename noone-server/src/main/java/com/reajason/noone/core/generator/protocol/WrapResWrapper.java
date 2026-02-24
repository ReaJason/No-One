package com.reajason.noone.core.generator.protocol;

import com.reajason.javaweb.memshell.ShellType;
import com.reajason.noone.core.generator.protocol.netty.NettyWrapResponseHeadersAdvice;
import com.reajason.noone.core.generator.protocol.netty.NettyWrapResponseStatusCodeAdvice;
import com.reajason.noone.core.generator.protocol.reactor.ReactorWrapResponseHeadersAdvice;
import com.reajason.noone.core.generator.protocol.reactor.ReactorWrapResponseStatusCodeAdvice;
import com.reajason.noone.core.generator.protocol.servlet.ServletWrapResponseHeadersAdvice;
import com.reajason.noone.core.generator.protocol.servlet.ServletWrapResponseStatusCodeAdvice;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.DynamicType;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class WrapResWrapper {
    public static DynamicType.Builder<?> extend(DynamicType.Builder<?> builder,
                                                String shellType,
                                                int statusCode, Map<String, String> headers) {
        var matcher = named("wrapResponse")
                .and(takesArguments(1))
                .and(returns(void.class));

        boolean reactorShell = isReactorShell(shellType);
        boolean nettyHttpShell = isNettyHttpShell(shellType);

        if (statusCode > 0) {
            Class<?> adviceClass = reactorShell ? ReactorWrapResponseStatusCodeAdvice.class
                    : nettyHttpShell ? NettyWrapResponseStatusCodeAdvice.class
                    : ServletWrapResponseStatusCodeAdvice.class;
            builder = builder.visit(Advice.withCustomMapping()
                    .bind(ProtocolAdviceBindings.ResponseStatusCode.class, statusCode)
                    .to(adviceClass)
                    .on(matcher));
        }

        String encodedHeaders = HttpProtocolMetadata.encodeHeaders(headers);
        if (StringUtils.isNoneBlank(encodedHeaders)) {
            Class<?> adviceClass = reactorShell ? ReactorWrapResponseHeadersAdvice.class
                    : nettyHttpShell ? NettyWrapResponseHeadersAdvice.class
                    : ServletWrapResponseHeadersAdvice.class;
            builder = builder.visit(Advice.withCustomMapping()
                    .bind(ProtocolAdviceBindings.ResponseEncodedHeaders.class, encodedHeaders)
                    .to(adviceClass)
                    .on(matcher));
        }
        return builder;
    }

    private static boolean isReactorShell(String shellType) {
        return ShellType.SPRING_WEBFLUX_WEB_FILTER.equals(shellType);
    }

    private static boolean isNettyHttpShell(String shellType) {
        return ShellType.NETTY_HANDLER.equals(shellType);
    }
}
