package com.reajason.noone.server.plugin.registry;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EnrichedCatalogEntry extends CatalogEntry {
    private boolean installed;
    private String installedVersion;
    private boolean updateAvailable;
}
