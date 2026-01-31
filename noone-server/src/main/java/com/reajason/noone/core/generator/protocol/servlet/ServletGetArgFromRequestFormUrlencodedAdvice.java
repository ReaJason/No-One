package com.reajason.noone.core.generator.protocol.servlet;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import net.bytebuddy.asm.Advice;

import javax.servlet.http.HttpServletRequest;

/**
 * Extracts payload from x-www-form-urlencoded request parameter.
 * Uses Object type to support both javax.servlet and jakarta.servlet APIs.
 */
public final class ServletGetArgFromRequestFormUrlencodedAdvice {

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
