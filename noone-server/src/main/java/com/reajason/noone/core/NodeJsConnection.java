package com.reajason.noone.core;

import com.reajason.noone.Constants;
import com.reajason.noone.core.client.Client;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class NodeJsConnection extends ShellConnection {

    public NodeJsConnection(Client client) {
        super(client);
    }

    @Override
    public void fillLoadPluginRequestMaps(String pluginName, byte[] pluginCodeBytes, Map<String, Object> requestMap) {
        requestMap.put(Constants.PLUGIN_CODE, "return " + new String(pluginCodeBytes, StandardCharsets.UTF_8));
    }
}
