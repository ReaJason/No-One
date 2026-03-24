package com.reajason.noone.core.generator.protocol.servlet;

import com.reajason.noone.core.generator.protocol.HttpProtocolMetadata;
import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import com.reajason.noone.core.profile.config.HttpRequestBodyType;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.DynamicType;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ServletGetPayloadFromRequestWrapper {

    public static DynamicType.Builder<?> extend(DynamicType.Builder<?> builder, HttpRequestBodyType requestBodyType, String requestTemplate) {

        HttpProtocolMetadata.PrefixSuffixIndexes requestIndexes = HttpProtocolMetadata.calculateRequestBodyIndexes(
                requestBodyType,
                requestTemplate
        );

        var matcher = named("getArgFromRequest")
                .and(takesArguments(1))
                .and(returns(byte[].class));

        Advice.WithCustomMapping mapping = Advice.withCustomMapping()
                .bind(ProtocolAdviceBindings.RequestPrefixLength.class, requestIndexes.prefixLength())
                .bind(ProtocolAdviceBindings.RequestSuffixLength.class, requestIndexes.suffixLength());

        if (requestBodyType == HttpRequestBodyType.FORM_URLENCODED) {
            String requestParameterName = HttpProtocolMetadata.extractParameterName(requestTemplate);
            return builder.visit(mapping
                    .bind(ProtocolAdviceBindings.RequestParameterName.class, requestParameterName)
                    .to(ServletGetArgFromRequestFormUrlencodedAdvice.class)
                    .on(matcher));
        }
        return builder.visit(mapping
                .to(ServletGetArgFromRequestBodyAdvice.class)
                .on(matcher));
    }

    /**
     * Extracts payload from request body by stripping template prefix/suffix bytes.
     * Uses Object type to support both javax.servlet and jakarta.servlet APIs.
     */
    public static final class ServletGetArgFromRequestBodyAdvice {

        @Advice.OnMethodExit
        public static void onExit(
                @Advice.Argument(0) Object requestArg,
                @ProtocolAdviceBindings.RequestPrefixLength int start,
                @ProtocolAdviceBindings.RequestSuffixLength int suffix,
                @Advice.Return(readOnly = false) byte[] returned
        ) throws Exception {
            HttpServletRequest request = (HttpServletRequest) requestArg;
            InputStream in = request.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            byte[] body = out.toByteArray();
            len = body.length - suffix - start;
            byte[] result = new byte[len];
            System.arraycopy(body, start, result, 0, len);
            returned = result;
        }
    }

    /**
     * Extracts payload from x-www-form-urlencoded request parameter.
     * Uses Object type to support both javax.servlet and jakarta.servlet APIs.
     */
    public static final class ServletGetArgFromRequestFormUrlencodedAdvice {

        @Advice.OnMethodExit
        public static void onExit(
                @Advice.Argument(0) Object request,
                @ProtocolAdviceBindings.RequestParameterName String parameterName,
                @ProtocolAdviceBindings.RequestPrefixLength int start,
                @ProtocolAdviceBindings.RequestSuffixLength int suffix,
                @Advice.Return(readOnly = false) byte[] returned
        ) throws Exception {
            String value = ((HttpServletRequest) request).getParameter(parameterName);
            returned = value.substring(start, value.length() - suffix).getBytes("UTF-8");
        }
    }
}
