package com.reajason.noone.core;

import com.alibaba.fastjson2.JSON;
import com.reajason.noone.Constants;
import com.reajason.noone.core.client.Client;

import java.util.HashMap;
import java.util.Map;

public class NodeJsConnection extends ShellConnection {
    public NodeJsConnection(Client client) {
        super(client);
    }

    @Override
    public void fillLoadPluginRequestMaps(String pluginName, byte[] pluginCodeBytes, Map<String, Object> requestMap) {
        requestMap.put(Constants.PLUGIN_CODE, "return " + new String(pluginCodeBytes));
    }

    @Override
    public byte[] serialize(Map<String, Object> map) {
        return JSON.toJSONBytes(map);
    }

    @Override
    public Map<String, Object> deserialize(byte[] data) {
        return JSON.parseObject(data);
    }
}
