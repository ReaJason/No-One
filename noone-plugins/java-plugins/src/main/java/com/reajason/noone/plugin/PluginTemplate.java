package com.reajason.noone.plugin;

import java.util.HashMap;
import java.util.Map;

public class PluginTemplate {

    /**
     * Plugin entry point. The returned value is serialized to the client as
     * {@code data}; there is no result key to write back into ctx.
     *
     * @param ctx request args plus runtime keys (pluginCaches, globalCaches,
     *            request, response)
     * @return any value supported by the TLV codec
     */
    public Object execute(Map<String, Object> ctx) {
        HashMap<String, Object> result = new HashMap<>();
        return result;
    }
}
