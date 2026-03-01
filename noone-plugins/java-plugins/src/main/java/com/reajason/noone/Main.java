package com.reajason.noone;

import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import com.reajason.noone.plugin.ClassFinder;
import com.reajason.noone.plugin.CommandExecutor;
import com.reajason.noone.plugin.FileManagerPlugin;
import com.reajason.noone.plugin.SystemInfoCollector;
import com.reajason.noone.plugin.ThreadDumpCollector;
import net.bytebuddy.ByteBuddy;

import java.util.Base64;

public class Main {
    public static void main(String[] args) {
        Class<?> targetClass = FileManagerPlugin.class;
        if (args.length > 0) {
            switch (args[0]) {
                case "command-execute":
                    targetClass = CommandExecutor.class;
                    break;
                case "thread-dump":
                    targetClass = ThreadDumpCollector.class;
                    break;
                case "class-finder":
                    targetClass = ClassFinder.class;
                    break;
                case "file-manager":
                    targetClass = FileManagerPlugin.class;
                    break;
                default:
                    targetClass = SystemInfoCollector.class;
                    break;
            }
        }
        System.out.println(Base64.getEncoder().encodeToString(
                new ByteBuddy().redefine(targetClass)
                        .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                        .make().getBytes()));
    }
}
