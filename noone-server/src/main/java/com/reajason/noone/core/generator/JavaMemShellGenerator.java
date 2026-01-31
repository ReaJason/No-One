package com.reajason.noone.core.generator;

import com.reajason.javaweb.memshell.*;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.server.AbstractServer;
import com.reajason.javaweb.memshell.server.ToolMapping;
import com.reajason.noone.Constants;
import com.reajason.noone.core.shelltool.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.reajason.javaweb.memshell.ShellType.*;

@Component
public class JavaMemShellGenerator {

    @PostConstruct
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
                .addShellClass(SERVLET, NoOneServlet.class)
                .addShellClass(JAKARTA_SERVLET, NoOneServlet.class)
                .addShellClass(FILTER, NoOneFilter.class)
                .addShellClass(JAKARTA_FILTER, NoOneFilter.class)
                .addShellClass(LISTENER, NoOneListener.class)
                .addShellClass(JAKARTA_LISTENER, NoOneListener.class)
                .addShellClass(NETTY_HANDLER, NoOneNettyHandler.class)
                .addShellClass(VALVE, NoOneValve.class)
                .addShellClass(JAKARTA_VALVE, NoOneValve.class)
                .addShellClass(SPRING_WEBFLUX_WEB_FILTER, NoOneWebFilter.class)
                .addShellClass(SPRING_WEBMVC_INTERCEPTOR, NoOneInterceptor.class)
                .addShellClass(SPRING_WEBMVC_JAKARTA_INTERCEPTOR, NoOneInterceptor.class)
                .build());

        ShellToolFactory.register(Constants.NO_ONE, NoOneMemShellGenerator.class, NoOneConfig.class);
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
