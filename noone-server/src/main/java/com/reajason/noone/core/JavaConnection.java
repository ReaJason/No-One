package com.reajason.noone.core;

import com.reajason.noone.Constants;
import com.reajason.noone.core.client.Client;
import org.objectweb.asm.ClassReader;

import java.util.Map;

/**
 * @author ReaJason
 * @since 2025/12/13
 */
public class JavaConnection extends ShellConnection {

    public JavaConnection(Client client) {
        super(client);
    }

    @Override
    public void fillLoadPluginRequestMaps(String pluginName, byte[] pluginCodeBytes, Map<String, Object> requestMap) {
        String className = new ClassReader(pluginCodeBytes).getClassName().replace("/", ".");
        requestMap.put(Constants.CLASSNAME, className);
        requestMap.put(Constants.CLASS_BYTES, pluginCodeBytes);
    }
}
