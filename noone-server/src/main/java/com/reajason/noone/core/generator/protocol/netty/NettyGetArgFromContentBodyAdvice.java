package com.reajason.noone.core.generator.protocol.netty;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import net.bytebuddy.asm.Advice;

/**
 * Extracts payload from Netty HttpContent bytes by stripping template prefix/suffix bytes.
 */
public final class NettyGetArgFromContentBodyAdvice {

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
