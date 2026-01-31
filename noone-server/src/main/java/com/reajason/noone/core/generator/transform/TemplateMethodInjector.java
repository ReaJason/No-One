package com.reajason.noone.core.generator.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.util.*;

public final class TemplateMethodInjector {
    private TemplateMethodInjector() {
    }

    public static byte[] inject(byte[] targetBytes, Class<?> templateClass, Set<MethodSig> rootMethods) {
        Objects.requireNonNull(targetBytes, "targetBytes");
        Objects.requireNonNull(templateClass, "templateClass");
        Objects.requireNonNull(rootMethods, "rootMethods");

        if (rootMethods.isEmpty()) {
            return targetBytes;
        }

        ClassNode targetNode = readClass(targetBytes);
        String targetInternalName = targetNode.name;

        ClassNode templateNode = readClass(readTemplateBytes(templateClass));
        String templateInternalName = templateNode.name;

        Map<MethodSig, MethodNode> templateMethods = new HashMap<>();
        for (MethodNode m : templateNode.methods) {
            templateMethods.put(new MethodSig(m.name, m.desc), m);
        }

        Set<MethodSig> needed = resolveClosure(templateMethods, templateInternalName, rootMethods);

        Set<MethodSig> existing = new HashSet<>();
        for (MethodNode m : targetNode.methods) {
            existing.add(new MethodSig(m.name, m.desc));
        }

        for (MethodSig sig : needed) {
            if (existing.contains(sig)) {
                continue;
            }
            MethodNode original = templateMethods.get(sig);
            if (original == null) {
                throw new IllegalStateException("Template method not found: " + sig);
            }
            MethodNode cloned = cloneMethod(original);
            rewriteOwners(cloned, templateInternalName, targetInternalName);
            targetNode.methods.add(cloned);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        targetNode.accept(writer);
        return writer.toByteArray();
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode(Opcodes.ASM9);
        reader.accept(node, 0);
        return node;
    }

    private static byte[] readTemplateBytes(Class<?> templateClass) {
        String resource = "/" + templateClass.getName().replace('.', '/') + ".class";
        try (InputStream in = templateClass.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Template class bytes not found: " + templateClass.getName());
            }
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load template class: " + templateClass.getName(), e);
        }
    }

    private static MethodNode cloneMethod(MethodNode original) {
        MethodNode cloned = new MethodNode(
                original.access,
                original.name,
                original.desc,
                original.signature,
                original.exceptions == null ? null : original.exceptions.toArray(new String[0])
        );
        original.accept(cloned);
        return cloned;
    }

    private static void rewriteOwners(MethodNode method, String fromOwner, String toOwner) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode m) {
                if (fromOwner.equals(m.owner)) {
                    m.owner = toOwner;
                }
            } else if (insn instanceof FieldInsnNode f) {
                if (fromOwner.equals(f.owner)) {
                    throw new IllegalStateException("Template method references template field: " + f.name);
                }
            } else if (insn instanceof TypeInsnNode t) {
                if (fromOwner.equals(t.desc)) {
                    t.desc = toOwner;
                }
            } else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type) {
                if (fromOwner.equals(type.getInternalName())) {
                    ldc.cst = Type.getObjectType(toOwner);
                }
            }
        }
    }

    private static Set<MethodSig> resolveClosure(
            Map<MethodSig, MethodNode> templateMethods,
            String templateInternalName,
            Set<MethodSig> roots
    ) {
        Deque<MethodSig> queue = new ArrayDeque<>(roots);
        Set<MethodSig> visited = new LinkedHashSet<>();

        while (!queue.isEmpty()) {
            MethodSig sig = queue.removeFirst();
            if (!visited.add(sig)) {
                continue;
            }

            MethodNode node = templateMethods.get(sig);
            if (node == null) {
                throw new IllegalStateException("Template method not found: " + sig);
            }

            for (AbstractInsnNode insn = node.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode m && templateInternalName.equals(m.owner)) {
                    MethodSig dep = new MethodSig(m.name, m.desc);
                    if (!visited.contains(dep)) {
                        queue.addLast(dep);
                    }
                } else if (insn instanceof FieldInsnNode f && templateInternalName.equals(f.owner)) {
                    throw new IllegalStateException("Template method references template field: " + sig + " -> " + f.name);
                }
            }
        }
        return visited;
    }
}
