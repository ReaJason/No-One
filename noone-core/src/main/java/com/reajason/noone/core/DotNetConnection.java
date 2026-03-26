package com.reajason.noone.core;

import com.reajason.noone.core.client.Client;
import com.reajason.noone.core.profile.Profile;

import java.util.Map;

/**
 * @author ReaJason
 * @since 2025/12/13
 */
public class DotNetConnection extends ShellConnection {

    public DotNetConnection(Client coreClient, Profile coreProfile) {
        super(coreClient, coreProfile);
    }

    public DotNetConnection(Client coreClient, Profile coreProfile,
                            Client loaderClient, Profile loaderProfile, String shellType) {
        super(coreClient, coreProfile, loaderClient, loaderProfile, shellType);
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
