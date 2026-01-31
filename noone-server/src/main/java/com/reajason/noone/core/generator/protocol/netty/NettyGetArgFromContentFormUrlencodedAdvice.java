package com.reajason.noone.core.generator.protocol.netty;

import com.reajason.noone.core.generator.protocol.ProtocolAdviceBindings;
import net.bytebuddy.asm.Advice;

/**
 * Extracts payload from x-www-form-urlencoded content bytes.
 *
 * <p>Parses the first matched parameter and applies prefix/suffix stripping on the decoded value.</p>
 */
public final class NettyGetArgFromContentFormUrlencodedAdvice {

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
            value = eq < 0 ? "" : part.substring(eq + 1);
            break;
        }
        returned = value.substring(start, value.length() - suffix).getBytes("UTF-8");
    }
}
