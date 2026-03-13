package com.reajason.noone.server.plugin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class JavaPluginPayloadService {

    public List<JavaPluginCandidate> buildCandidates(Plugin plugin, byte[] originalPayloadBytes) {
        List<String> classNames = candidateClassNames(plugin);
        if (classNames.isEmpty()) {
            return List.of(new JavaPluginCandidate(readClassName(originalPayloadBytes), originalPayloadBytes));
        }

        String sourceInternalName = new ClassReader(originalPayloadBytes).getClassName();
        List<String> shuffled = new ArrayList<>(classNames);
        Collections.shuffle(shuffled);

        List<JavaPluginCandidate> candidates = new ArrayList<>(shuffled.size());
        for (String className : shuffled) {
            candidates.add(new JavaPluginCandidate(className, rewriteClassName(originalPayloadBytes, sourceInternalName, className)));
        }
        return candidates;
    }

    @SuppressWarnings("unchecked")
    private List<String> candidateClassNames(Plugin plugin) {
        if (plugin == null || plugin.getMeta() == null) {
            return List.of();
        }
        Object classNamesObj = plugin.getMeta().get("classNames");
        if (!(classNamesObj instanceof List<?> rawClassNames)) {
            return List.of();
        }
        Set<String> uniqueClassNames = new LinkedHashSet<>();
        for (Object item : rawClassNames) {
            if (item == null) {
                continue;
            }
            String className = String.valueOf(item).trim();
            if (!className.isEmpty()) {
                uniqueClassNames.add(className);
            }
        }
        return new ArrayList<>(uniqueClassNames);
    }

    private String readClassName(byte[] payloadBytes) {
        return new ClassReader(payloadBytes).getClassName().replace('/', '.');
    }

    private byte[] rewriteClassName(byte[] payloadBytes, String sourceInternalName, String targetClassName) {
        String targetInternalName = targetClassName.replace('.', '/');
        ClassReader reader = new ClassReader(payloadBytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassRemapper remapper = new ClassRemapper(writer, new SimpleRemapper(Map.of(sourceInternalName, targetInternalName)));
        reader.accept(remapper, 0);
        return writer.toByteArray();
    }

    public record JavaPluginCandidate(String className, byte[] payloadBytes) {
    }
}
