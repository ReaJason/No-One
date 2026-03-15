package com.reajason.noone.core;

import com.reajason.noone.Constants;
import com.reajason.noone.server.profile.Profile;

import java.util.Map;

/**
 * @author ReaJason
 * @since 2025/12/13
 */
public class DotNetConnection extends ShellConnection {

    public DotNetConnection(ConnectionConfig config) {
        super(config);
    }

    @Override
    protected byte[] getCoreBytes(String shellType, Profile loaderProfile) {
        return new byte[0];
    }

    @Override
    public void fillLoadPluginRequestMaps(String pluginName, byte[] pluginCodeBytes, Map<String, Object> requestMap) {
        requestMap.put(Constants.PLUGIN_BYTES, pluginCodeBytes);
    }
}
