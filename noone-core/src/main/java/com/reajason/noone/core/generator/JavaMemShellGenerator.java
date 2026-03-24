package com.reajason.noone.core.generator;

import com.reajason.javaweb.memshell.*;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.server.AbstractServer;
import com.reajason.javaweb.memshell.server.ToolMapping;
import com.reajason.noone.core.Constants;
import com.reajason.noone.core.generator.config.NoOneConfig;
import com.reajason.noone.core.generator.memshell.NoOneStagelessGenerator;
import com.reajason.noone.core.generator.memshell.NoOneStagingGenerator;
import com.reajason.noone.core.shelltool.*;

import java.util.*;

import static com.reajason.javaweb.memshell.ShellType.*;

public class JavaMemShellGenerator {

    public void init() {
        ServerFactory.addToolMapping(ShellTool.Godzilla, ToolMapping.builder().build());
        ServerFactory.addToolMapping(ShellTool.Command, ToolMapping.builder().build());
        ServerFactory.addToolMapping(ShellTool.Suo5, ToolMapping.builder().build());
        ServerFactory.addToolMapping(ShellTool.Suo5v2, ToolMapping.builder().build());
        ServerFactory.addToolMapping(ShellTool.Behinder, ToolMapping.builder().build());
        ServerFactory.addToolMapping(ShellTool.NeoreGeorg, ToolMapping.builder().build());
        ServerFactory.addToolMapping(ShellTool.Proxy, ToolMapping.builder().build());
        ServerFactory.addToolMapping(ShellTool.AntSword, ToolMapping.builder().build());

        ServerFactory.addToolMapping(Constants.NO_ONE, ToolMapping.builder()
                .addShellClass(SERVLET, NoOneStagelessServlet.class)
                .addShellClass(JAKARTA_SERVLET, NoOneStagelessServlet.class)
                .addShellClass(FILTER, NoOneStagelessFilter.class)
                .addShellClass(JAKARTA_FILTER, NoOneStagelessFilter.class)
                .addShellClass(LISTENER, NoOneStagelessListener.class)
                .addShellClass(JAKARTA_LISTENER, NoOneStagelessListener.class)
                .addShellClass(NETTY_HANDLER, NoOneStagelessNettyHandler.class)
                .addShellClass(WEBSOCKET, NoOneStagelessWebSocket.class)
                .addShellClass(DUBBO_SERVICE, NoOneStagelessDubboService.class)
                .addShellClass(VALVE, NoOneStagelessValve.class)
                .addShellClass(JAKARTA_VALVE, NoOneStagelessValve.class)
                .addShellClass(ACTION, NoOneStagelessStruct2Action.class)
                .addShellClass(SPRING_WEBFLUX_WEB_FILTER, NoOneStagelessWebFilter.class)
                .addShellClass(SPRING_WEBMVC_INTERCEPTOR, NoOneStagelessInterceptor.class)
                .addShellClass(SPRING_WEBMVC_JAKARTA_INTERCEPTOR, NoOneStagelessInterceptor.class)
                .build());

        ServerFactory.addToolMapping(Constants.NO_ONE_STAGING, ToolMapping.builder()
                .addShellClass(SERVLET, NoOneStagingServlet.class)
                .addShellClass(JAKARTA_SERVLET, NoOneStagingServlet.class)
                .addShellClass(FILTER, NoOneStagingFilter.class)
                .addShellClass(JAKARTA_FILTER, NoOneStagingFilter.class)
                .addShellClass(LISTENER, NoOneStagingListener.class)
                .addShellClass(JAKARTA_LISTENER, NoOneStagingListener.class)
                .addShellClass(NETTY_HANDLER, NoOneStagingNettyHandler.class)
                .addShellClass(VALVE, NoOneStagingValve.class)
                .addShellClass(JAKARTA_VALVE, NoOneStagingValve.class)
                .addShellClass(ACTION, NoOneStagingStruct2Action.class)
                .addShellClass(SPRING_WEBFLUX_WEB_FILTER, NoOneStagingWebFilter.class)
                .addShellClass(SPRING_WEBMVC_INTERCEPTOR, NoOneStagingInterceptor.class)
                .addShellClass(SPRING_WEBMVC_JAKARTA_INTERCEPTOR, NoOneStagingInterceptor.class)
                .build());

        ShellToolFactory.register(Constants.NO_ONE, NoOneStagelessGenerator.class, NoOneConfig.class);
        ShellToolFactory.register(Constants.NO_ONE_STAGING, NoOneStagingGenerator.class, NoOneConfig.class);
    }

    public MemShellResult generate(ShellConfig shellConfig,
                                   InjectorConfig injectorConfig,
                                   ShellToolConfig shellToolConfig) {
        return MemShellGenerator.generate(shellConfig, injectorConfig, shellToolConfig);
    }

    public Map<String, Map<?, ?>> getConfigMap() {
        Map<String, Map<?, ?>> coreMap = new HashMap<>(16);
        List<String> supportedServers = ServerFactory.getSupportedServers();
        for (String supportedServer : supportedServers) {
            AbstractServer server = ServerFactory.getServer(supportedServer);
            Map<String, Set<String>> map = new LinkedHashMap<>(16);
            for (String shellTool : server.getSupportedShellTools()) {
                if (!Constants.NO_ONE.equals(shellTool)) {
                    continue;
                }
                Set<String> supportedShellTypes = server.getSupportedShellTypes(shellTool);
                if (supportedShellTypes.isEmpty()) {
                    continue;
                }
                map.put(shellTool, supportedShellTypes);
            }
            coreMap.put(supportedServer, map);
        }
        return coreMap;
    }
}
