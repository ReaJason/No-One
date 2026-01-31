package com.reajason.noone.core.generator.protocol;

import java.lang.annotation.*;

public final class ProtocolAdviceBindings {

    private ProtocolAdviceBindings() {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface RequestPrefixLength {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface RequestSuffixLength {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface RequestParameterName {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface ResponsePrefixBase64 {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface ResponseSuffixBase64 {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface ResponseStatusCode {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface ResponseEncodedHeaders {
    }
}
