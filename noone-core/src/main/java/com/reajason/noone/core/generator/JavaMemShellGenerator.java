package com.reajason.noone.core.generator;

import com.reajason.javaweb.ShellGenerator;
import com.reajason.javaweb.memshell.MemShellGenerator;
import com.reajason.javaweb.memshell.MemShellRegistry;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.server.AbstractServer;
import com.reajason.javaweb.memshell.server.ToolMapping;
import com.reajason.javaweb.memshell.spi.ShellToolProvider;
import com.reajason.noone.core.Constants;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.generator.memshell.NoOneStagelessGenerator;
import com.reajason.noone.core.generator.memshell.NoOneStagingGenerator;
import com.reajason.noone.core.shelltool.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.reajason.javaweb.memshell.ShellType.*;

public class JavaMemShellGenerator {

    private final MemShellGenerator memShellGenerator = new MemShellGenerator();

    static {
        MemShellRegistry registry = MemShellRegistry.getInstance();
        registry.registerTool(Constants.NO_ONE,
                provider(NoOneStagelessGenerator.class, stagelessMapping()));
        registry.registerTool(Constants.NO_ONE_STAGING,
                provider(NoOneStagingGenerator.class, stagingMapping()));
    }

    private static ShellToolProvider provider(Class<? extends ShellGenerator> generatorClass,
                                              ToolMapping toolMapping) {
        return new ShellToolProvider() {
            @Override
            public ToolMapping getToolMapping() {
                return toolMapping;
            }

            @Override
            public Class<? extends ShellToolConfig> getConfigClass() {
                return NoOneConfig.class;
            }

            @Override
            public Class<? extends ShellGenerator> getGeneratorClass() {
                return generatorClass;
            }
        };
    }

    private static ToolMapping stagelessMapping() {
        return ToolMapping.builder()
                .addShellClass(SERVLET, NoOneStagelessServlet.class.getName())
                .addShellClass(JAKARTA_SERVLET, NoOneStagelessServlet.class.getName())
                .addShellClass(FILTER, NoOneStagelessFilter.class.getName())
                .addShellClass(JAKARTA_FILTER, NoOneStagelessFilter.class.getName())
                .addShellClass(LISTENER, NoOneStagelessListener.class.getName())
                .addShellClass(JAKARTA_LISTENER, NoOneStagelessListener.class.getName())
                .addShellClass(NETTY_HANDLER, NoOneStagelessNettyHandler.class.getName())
                .addShellClass(WEBSOCKET, NoOneStagelessWebSocket.class.getName())
                .addShellClass(DUBBO_SERVICE, NoOneStagelessDubboService.class.getName())
                .addShellClass(VALVE, NoOneStagelessValve.class.getName())
                .addShellClass(JAKARTA_VALVE, NoOneStagelessValve.class.getName())
                .addShellClass(ACTION, NoOneStagelessStruct2Action.class.getName())
                .addShellClass(SPRING_WEBFLUX_WEB_FILTER, NoOneStagelessWebFilter.class.getName())
                .addShellClass(SPRING_WEBMVC_INTERCEPTOR, NoOneStagelessInterceptor.class.getName())
                .addShellClass(SPRING_WEBMVC_JAKARTA_INTERCEPTOR, NoOneStagelessInterceptor.class.getName())
                .build();
    }

    private static ToolMapping stagingMapping() {
        return ToolMapping.builder()
                .addShellClass(SERVLET, NoOneStagingServlet.class.getName())
                .addShellClass(JAKARTA_SERVLET, NoOneStagingServlet.class.getName())
                .addShellClass(FILTER, NoOneStagingFilter.class.getName())
                .addShellClass(JAKARTA_FILTER, NoOneStagingFilter.class.getName())
                .addShellClass(LISTENER, NoOneStagingListener.class.getName())
                .addShellClass(JAKARTA_LISTENER, NoOneStagingListener.class.getName())
                .addShellClass(NETTY_HANDLER, NoOneStagingNettyHandler.class.getName())
                .addShellClass(VALVE, NoOneStagingValve.class.getName())
                .addShellClass(JAKARTA_VALVE, NoOneStagingValve.class.getName())
                .addShellClass(ACTION, NoOneStagingStruct2Action.class.getName())
                .addShellClass(SPRING_WEBFLUX_WEB_FILTER, NoOneStagingWebFilter.class.getName())
                .addShellClass(SPRING_WEBMVC_INTERCEPTOR, NoOneStagingInterceptor.class.getName())
                .addShellClass(SPRING_WEBMVC_JAKARTA_INTERCEPTOR, NoOneStagingInterceptor.class.getName())
                .build();
    }

    public MemShellResult generate(ShellConfig shellConfig,
                                   InjectorConfig injectorConfig,
                                   ShellToolConfig shellToolConfig) {
        return memShellGenerator.generate(shellConfig, injectorConfig, shellToolConfig);
    }

    public List<String> getSupportedServers() {
        return memShellGenerator.getSupportedServers();
    }

    public AbstractServer getServer(String serverName) {
        return memShellGenerator.getServer(serverName);
    }

    public Map<String, Map<?, ?>> getConfigMap() {
        Map<String, Map<?, ?>> coreMap = new LinkedHashMap<>(16);
        Map<String, Map<String, List<String>>> catalog = memShellGenerator.getShellTypeCatalog().asMap();
        for (Map.Entry<String, Map<String, List<String>>> entry : catalog.entrySet()) {
            List<String> shellTypes = entry.getValue().get(Constants.NO_ONE);
            if (shellTypes != null && !shellTypes.isEmpty()) {
                coreMap.put(entry.getKey(), Collections.singletonMap(Constants.NO_ONE, shellTypes));
            }
        }
        return coreMap;
    }
}
