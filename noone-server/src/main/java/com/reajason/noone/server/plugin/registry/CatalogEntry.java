package com.reajason.noone.server.plugin.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogEntry {
    private String id;
    private String name;
    private String version;
    private String language;
    private String author;
    private String description;
    private String type;
    private String downloadUrl;
}
