package com.reajason.noone.core.generator.protocol.reactor;

import com.reajason.noone.core.generator.protocol.HttpProtocolMetadata;
import com.reajason.noone.core.profile.config.HttpRequestBodyType;
import net.bytebuddy.dynamic.DynamicType;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class ReactorGetPayloadFromRequestWrapper {
    public static DynamicType.Builder<?> extend(DynamicType.Builder<?> builder, HttpRequestBodyType requestBodyType, String requestTemplate) {
        HttpProtocolMetadata.PrefixSuffixIndexes requestIndexes = HttpProtocolMetadata.calculateRequestBodyIndexes(
                requestBodyType,
                requestTemplate
        );
        int prefix = requestIndexes.prefixLength();
        int suffix = requestIndexes.suffixLength();
        builder = builder
                .field(named("prefix")).value(prefix)
                .field(named("suffix")).value(suffix);
        if (requestBodyType == HttpRequestBodyType.FORM_URLENCODED) {
            String requestParameterName = HttpProtocolMetadata.extractParameterName(requestTemplate);
            builder = builder.field(named("paramName")).value(requestParameterName);
        }
        return builder;
    }
}
