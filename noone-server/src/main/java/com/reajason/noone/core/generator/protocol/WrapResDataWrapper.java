package com.reajason.noone.core.generator.protocol;

import com.reajason.javaweb.buddy.MethodCallReplaceVisitorWrapper;
import com.reajason.noone.core.transform.TransformSupport;
import com.reajason.noone.server.profile.config.HttpResponseBodyType;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.DynamicType;

import java.util.Base64;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class WrapResDataWrapper {

    public static DynamicType.Builder<?> extend(DynamicType.Builder<?> builder,
                                                String className,
                                                HttpResponseBodyType responseBodyType, String requestTemplate) {
        HttpProtocolMetadata.ResponsePrefixSuffix responseParts =
                HttpProtocolMetadata.calculateResponseParts(
                        responseBodyType,
                        requestTemplate
                );

        var matcher = named("wrapResData")
                .and(takesArguments(1))
                .and(returns(byte[].class));

        String prefixBase64 = Base64.getEncoder().encodeToString(responseParts.prefixBytes());
        String suffixBase64 = Base64.getEncoder().encodeToString(responseParts.suffixBytes());

        return builder
                .visit(MethodCallReplaceVisitorWrapper.newInstance(
                        "wrapResData", className, TransformSupport.class.getName()))
                .visit(Advice.withCustomMapping()
                        .bind(ProtocolAdviceBindings.ResponsePrefixBase64.class, prefixBase64)
                        .bind(ProtocolAdviceBindings.ResponseSuffixBase64.class, suffixBase64)
                        .to(WrapResDataAdvice.class)
                        .on(matcher));
    }

    /**
     * Applies response template prefix/suffix in wrapResData(byte[]).
     */
    public static final class WrapResDataAdvice {

        @Advice.OnMethodExit
        public static void onExit(
                @Advice.Argument(0) byte[] payload,
                @ProtocolAdviceBindings.ResponsePrefixBase64 String prefixBase64,
                @ProtocolAdviceBindings.ResponseSuffixBase64 String suffixBase64,
                @Advice.Return(readOnly = false) byte[] returned
        ) {
            byte[] prefixBytes = prefixBase64.isEmpty() ? new byte[0] : TransformSupport.decodeBase64(prefixBase64);
            byte[] suffixBytes = suffixBase64.isEmpty() ? new byte[0] : TransformSupport.decodeBase64(suffixBase64);

            byte[] result = new byte[prefixBytes.length + payload.length + suffixBytes.length];
            int offset = 0;
            if (prefixBytes.length > 0) {
                System.arraycopy(prefixBytes, 0, result, 0, prefixBytes.length);
                offset += prefixBytes.length;
            }
            System.arraycopy(payload, 0, result, offset, payload.length);
            offset += payload.length;
            if (suffixBytes.length > 0) {
                System.arraycopy(suffixBytes, 0, result, offset, suffixBytes.length);
            }
            returned = result;
        }
    }
}
