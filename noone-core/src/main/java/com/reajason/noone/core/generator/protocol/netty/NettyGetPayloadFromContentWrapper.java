package com.reajason.noone.core.generator.protocol.netty;

import com.reajason.noone.core.generator.protocol.HttpProtocolMetadata;
import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import com.reajason.noone.core.profile.config.HttpRequestBodyType;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.DynamicType;

import java.net.URLDecoder;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class NettyGetPayloadFromContentWrapper {

    public static DynamicType.Builder<?> extend(DynamicType.Builder<?> builder, HttpRequestBodyType requestBodyType, String requestTemplate) {
        HttpProtocolMetadata.PrefixSuffixIndexes requestIndexes = HttpProtocolMetadata.calculateRequestBodyIndexes(
                requestBodyType,
                requestTemplate
        );

        var matcher = named("getArgFromContent")
                .and(takesArguments(byte[].class))
                .and(returns(byte[].class));

        Advice.WithCustomMapping mapping = Advice.withCustomMapping()
                .bind(ProtocolAdviceBindings.RequestPrefixLength.class, requestIndexes.prefixLength())
                .bind(ProtocolAdviceBindings.RequestSuffixLength.class, requestIndexes.suffixLength());

        if (requestBodyType == HttpRequestBodyType.FORM_URLENCODED) {
            String requestParameterName = HttpProtocolMetadata.extractParameterName(requestTemplate);
            return builder.visit(mapping
                    .bind(ProtocolAdviceBindings.RequestParameterName.class, requestParameterName)
                    .to(NettyGetArgFromContentFormUrlencodedAdvice.class)
                    .on(matcher));
        }

        return builder.visit(mapping
                .to(NettyGetArgFromContentBodyAdvice.class)
                .on(matcher));
    }

    /**
     * Extracts payload from Netty HttpContent bytes by stripping template prefix/suffix bytes.
     */
    public static final class NettyGetArgFromContentBodyAdvice {

        @Advice.OnMethodExit
        public static void onExit(
                @Advice.Argument(0) byte[] content,
                @ProtocolAdviceBindings.RequestPrefixLength int start,
                @ProtocolAdviceBindings.RequestSuffixLength int suffix,
                @Advice.Return(readOnly = false) byte[] returned
        ) {
            int len = content.length - suffix - start;
            byte[] result = new byte[len];
            System.arraycopy(content, start, result, 0, len);
            returned = result;
        }
    }

    /**
     * Extracts payload from x-www-form-urlencoded content bytes.
     *
     * <p>Parses the first matched parameter and applies prefix/suffix stripping on the decoded value.</p>
     */
    public static final class NettyGetArgFromContentFormUrlencodedAdvice {

        @Advice.OnMethodExit
        public static void onExit(
                @Advice.Argument(0) byte[] content,
                @ProtocolAdviceBindings.RequestParameterName String parameterName,
                @ProtocolAdviceBindings.RequestPrefixLength int start,
                @ProtocolAdviceBindings.RequestSuffixLength int suffix,
                @Advice.Return(readOnly = false) byte[] returned
        ) throws Exception {
            String body = new String(content, "UTF-8");
            String value = null;
            int offset = 0;
            while (offset <= body.length()) {
                int amp = body.indexOf('&', offset);
                String part;
                if (amp < 0) {
                    part = body.substring(offset);
                    offset = body.length() + 1;
                } else {
                    part = body.substring(offset, amp);
                    offset = amp + 1;
                }
                if (part.isEmpty()) {
                    continue;
                }
                int eq = part.indexOf('=');
                String key = eq < 0 ? part : part.substring(0, eq);
                if (!parameterName.equals(key)) {
                    continue;
                }
                value = URLDecoder.decode(eq < 0 ? "" : part.substring(eq + 1), "UTF-8");
                break;
            }
            returned = value.substring(start, value.length() - suffix).getBytes("UTF-8");
        }
    }
}
