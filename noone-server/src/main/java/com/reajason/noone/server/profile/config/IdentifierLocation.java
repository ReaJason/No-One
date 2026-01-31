package com.reajason.noone.server.profile.config;

/**
 * Identifier location for matching a profile.
 *
 * @author ReaJason
 */
public enum IdentifierLocation {
    /**
     * HTTP Header / gRPC Metadata.
     */
    HEADER,
    /**
     * HTTP Cookie.
     */
    COOKIE,
    /**
     * HTTP query parameter.
     */
    QUERY_PARAM,
    /**
     * Generic metadata location.
     */
    METADATA,
}
