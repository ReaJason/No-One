package com.reajason.noone.server.shell;

import com.reajason.noone.server.plugin.Plugin;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class ShellPluginPayloadResolver {

    public byte[] resolve(ShellLanguage language, Plugin plugin) {
        if (plugin.getPayload() == null || plugin.getPayload().isBlank()) {
            throw new IllegalArgumentException("Plugin payload is empty: " + plugin.getPluginId());
        }
        return switch (language) {
            case JAVA -> decodeBase64Payload(plugin, language);
            case DOTNET -> validateDotNetAssemblyBytes(plugin, decodeBase64Payload(plugin, language));
            case NODEJS -> plugin.getPayload().getBytes(StandardCharsets.UTF_8);
        };
    }

    private byte[] decodeBase64Payload(Plugin plugin, ShellLanguage language) {
        try {
            return Base64.getDecoder().decode(plugin.getPayload());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid base64 payload for plugin [" + plugin.getPluginId() + "] language [" + language.getValue() + "]", e);
        }
    }

    private byte[] validateDotNetAssemblyBytes(Plugin plugin, byte[] payloadBytes) {
        if (payloadBytes.length < 2 || payloadBytes[0] != 'M' || payloadBytes[1] != 'Z') {
            throw new IllegalArgumentException("DOTNET plugin payload is not a valid assembly: " + plugin.getPluginId());
        }
        return payloadBytes;
    }
}
