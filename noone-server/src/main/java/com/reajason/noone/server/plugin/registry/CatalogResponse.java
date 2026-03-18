package com.reajason.noone.server.plugin.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogResponse {
    private int version;
    private List<CatalogEntry> plugins;
}
