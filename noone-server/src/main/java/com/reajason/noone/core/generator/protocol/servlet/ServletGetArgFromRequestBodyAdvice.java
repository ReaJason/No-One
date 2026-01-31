package com.reajason.noone.core.generator.protocol.servlet;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import net.bytebuddy.asm.Advice;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Extracts payload from request body by stripping template prefix/suffix bytes.
 * Uses Object type to support both javax.servlet and jakarta.servlet APIs.
 */
public final class ServletGetArgFromRequestBodyAdvice {

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
