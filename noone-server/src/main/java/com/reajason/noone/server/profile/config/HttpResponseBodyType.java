package com.reajason.noone.server.profile.config;

/**
 * HTTP request body type when payload is placed in body.
 */
public enum HttpResponseBodyType {
    TEXT,
    FORM_URLENCODED,
    MULTIPART_FORM_DATA,
    JSON,
    XML,
    BINARY
}
