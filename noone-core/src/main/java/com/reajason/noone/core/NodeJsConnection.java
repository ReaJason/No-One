package com.reajason.noone.core;


import com.reajason.noone.core.profile.Profile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class NodeJsConnection extends ShellConnection {

    public NodeJsConnection(ConnectionConfig config) {
        super(config);
    }

    @Override
    protected byte[] getCoreBytes(String shellType, Profile loaderProfile) {
        return new byte[0];
    }

    @Override
    public void fillLoadPluginRequestMaps(String pluginName, byte[] pluginCodeBytes, Map<String, Object> requestMap) {
        requestMap.put(Constants.PLUGIN_CODE, "return " + new String(pluginCodeBytes, StandardCharsets.UTF_8));
    }
}
