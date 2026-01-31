package com.reajason.noone.server.profile.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Identifier configuration used to match a profile from an incoming request.
 *
 * @author ReaJason
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentifierConfig {

    private IdentifierLocation location;

    private IdentifierOperator operator;

    private String name;

    private String value;
}

