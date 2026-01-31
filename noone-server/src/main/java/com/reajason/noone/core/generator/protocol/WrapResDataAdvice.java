package com.reajason.noone.core.generator.protocol;

import com.reajason.noone.core.transform.TransformSupport;
import net.bytebuddy.asm.Advice;

/**
 * Applies response template prefix/suffix in wrapResData(byte[]).
 */
public final class WrapResDataAdvice {

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
