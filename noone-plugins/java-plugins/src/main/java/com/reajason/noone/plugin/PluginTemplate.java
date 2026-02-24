package com.reajason.noone.plugin;

import java.util.HashMap;
import java.util.Map;

public class PluginTemplate {

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Map)) {
            return true;
        }
        Map<String, Object> ctx = (Map<String, Object>) obj;
        HashMap<String, Object> result = new HashMap<>();
        ctx.put("result", result);
        return true;
    }
}
