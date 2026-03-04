package com.reajason.noone.core;

import com.reajason.noone.Constants;
import com.reajason.noone.core.client.Client;
import org.objectweb.asm.ClassReader;

import java.util.Map;

/**
 * @author ReaJason
 * @since 2025/12/13
 */
public class DotNetConnection extends ShellConnection {

    public DotNetConnection(Client client) {
        super(client);
    }

    @Override
    public void fillLoadPluginRequestMaps(String pluginName, byte[] pluginCodeBytes, Map<String, Object> requestMap) {
        requestMap.put(Constants.PLUGIN_BYTES, pluginCodeBytes);
    }
}
