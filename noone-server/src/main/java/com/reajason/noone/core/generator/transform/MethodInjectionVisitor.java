package com.reajason.noone.core.generator.transform;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.pool.TypePool;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.util.*;

public class MethodInjectionVisitor implements net.bytebuddy.asm.AsmVisitorWrapper {
    private final Class<?> templateClass;
    private final Set<MethodSig> rootMethods;

    public MethodInjectionVisitor(Class<?> templateClass, Set<MethodSig> rootMethods) {
        this.templateClass = Objects.requireNonNull(templateClass, "templateClass");
        this.rootMethods = Objects.requireNonNull(rootMethods, "rootMethods");
    }

    @Override
    public int mergeWriter(int flags) {
        return flags | ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
    }

    @Override
    public int mergeReader(int flags) {
        return flags;
    }

    @Override
    public net.bytebuddy.jar.asm.ClassVisitor wrap(
            TypeDescription instrumentedType,
            net.bytebuddy.jar.asm.ClassVisitor classVisitor,
            Implementation.Context implementationContext,
            TypePool typePool,
            FieldList<FieldDescription.InDefinedShape> fields,
            MethodList<?> methods,
            int writerFlags,
            int readerFlags
    ) {
        if (rootMethods.isEmpty()) {
            return classVisitor;
        }
        return new ByteBuddyToObjectWebAdapter(
                classVisitor,
                templateClass,
                rootMethods,
                instrumentedType.getInternalName()
        );
    }

    /**
     * Adapter that bridges ByteBuddy's shaded ASM (net.bytebuddy.jar.asm) to ObjectWeb ASM (org.objectweb.asm)
     */
    private static class ByteBuddyToObjectWebAdapter extends net.bytebuddy.jar.asm.ClassVisitor {
        private final Class<?> templateClass;
        private final Set<MethodSig> rootMethods;
        private final String targetInternalName;
        private final Set<MethodSig> existingMethods;

        public ByteBuddyToObjectWebAdapter(
                net.bytebuddy.jar.asm.ClassVisitor cv,
                Class<?> templateClass,
                Set<MethodSig> rootMethods,
                String targetInternalName
        ) {
            super(net.bytebuddy.jar.asm.Opcodes.ASM9, cv);
            this.templateClass = templateClass;
            this.rootMethods = rootMethods;
            this.targetInternalName = targetInternalName;
            this.existingMethods = new HashSet<>();
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public net.bytebuddy.jar.asm.MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions
        ) {
            existingMethods.add(new MethodSig(name, descriptor));
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            try {
                injectMethods();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to inject helper methods", e);
            }
            super.visitEnd();
        }

        private void injectMethods() throws Exception {
            ClassNode templateNode = readTemplateClass();
            String templateInternalName = templateNode.name;

            Map<MethodSig, MethodNode> templateMethods = new HashMap<>();
            for (MethodNode m : templateNode.methods) {
                templateMethods.put(new MethodSig(m.name, m.desc), m);
            }

            Set<MethodSig> needed = resolveClosure(templateMethods, templateInternalName, rootMethods);

            for (MethodSig sig : needed) {
                if (existingMethods.contains(sig)) {
                    continue;
                }
                MethodNode original = templateMethods.get(sig);
                if (original == null) {
                    throw new IllegalStateException("Template method not found: " + sig);
                }
                MethodNode cloned = cloneMethod(original);
                rewriteOwners(cloned, templateInternalName, targetInternalName);

                // Convert ObjectWeb ASM to ByteBuddy ASM by writing and reading bytes
                ClassWriter cw = new ClassWriter(0);
                cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "Temp", null, "java/lang/Object", null);
                cloned.accept(cw);

                // Extract just the method bytecode and inject it via ByteBuddy's visitor
                net.bytebuddy.jar.asm.MethodVisitor mv = cv.visitMethod(
                        cloned.access,
                        cloned.name,
                        cloned.desc,
                        cloned.signature,
                        cloned.exceptions != null ? cloned.exceptions.toArray(new String[0]) : null
                );

                if (mv != null) {
                    copyMethodBody(cloned, mv);
                }
            }
        }

        private void copyMethodBody(MethodNode source, net.bytebuddy.jar.asm.MethodVisitor target) {
            target.visitCode();

            // Visit method body by accepting to an ObjectWeb visitor that delegates to ByteBuddy visitor
            source.accept(new ObjectWebToByteBuddyMethodAdapter(target));

            target.visitMaxs(source.maxStack, source.maxLocals);
            target.visitEnd();
        }

        private ClassNode readTemplateClass() throws Exception {
            String resource = "/" + templateClass.getName().replace('.', '/') + ".class";
            try (InputStream in = templateClass.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Template class not found: " + templateClass.getName());
                }
                byte[] bytes = in.readAllBytes();
                ClassReader reader = new ClassReader(bytes);
                ClassNode node = new ClassNode(Opcodes.ASM9);
                reader.accept(node, 0);
                return node;
            }
        }

        private MethodNode cloneMethod(MethodNode original) {
            MethodNode cloned = new MethodNode(
                    original.access,
                    original.name,
                    original.desc,
                    original.signature,
                    original.exceptions != null ? original.exceptions.toArray(new String[0]) : null
            );
            original.accept(cloned);
            return cloned;
        }

        private void rewriteOwners(MethodNode method, String fromOwner, String toOwner) {
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
                } else if (insn instanceof LdcInsnNode ldc) {
                    if (ldc.cst instanceof Type type) {
                        if (fromOwner.equals(type.getInternalName())) {
                            ldc.cst = Type.getObjectType(toOwner);
                        }
                    }
                }
            }
        }

        private Set<MethodSig> resolveClosure(
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

    /**
     * Adapter that converts ObjectWeb ASM MethodVisitor calls to ByteBuddy's shaded ASM MethodVisitor calls
     */
    private static class ObjectWebToByteBuddyMethodAdapter extends MethodVisitor {
        private final net.bytebuddy.jar.asm.MethodVisitor target;

        public ObjectWebToByteBuddyMethodAdapter(net.bytebuddy.jar.asm.MethodVisitor target) {
            super(Opcodes.ASM9);
            this.target = target;
        }

        @Override
        public void visitInsn(int opcode) {
            target.visitInsn(opcode);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            target.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            target.visitVarInsn(opcode, varIndex);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            target.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            target.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            target.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            // Convert Handle and arguments
            net.bytebuddy.jar.asm.Handle bbHandle = convertHandle(bootstrapMethodHandle);
            Object[] bbArgs = convertBootstrapArgs(bootstrapMethodArguments);
            target.visitInvokeDynamicInsn(name, descriptor, bbHandle, bbArgs);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            target.visitJumpInsn(opcode, convertLabel(label));
        }

        @Override
        public void visitLabel(Label label) {
            target.visitLabel(convertLabel(label));
        }

        @Override
        public void visitLdcInsn(Object value) {
            target.visitLdcInsn(convertConstant(value));
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            target.visitIincInsn(varIndex, increment);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            net.bytebuddy.jar.asm.Label bbDflt = convertLabel(dflt);
            net.bytebuddy.jar.asm.Label[] bbLabels = new net.bytebuddy.jar.asm.Label[labels.length];
            for (int i = 0; i < labels.length; i++) {
                bbLabels[i] = convertLabel(labels[i]);
            }
            target.visitTableSwitchInsn(min, max, bbDflt, bbLabels);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            net.bytebuddy.jar.asm.Label bbDflt = convertLabel(dflt);
            net.bytebuddy.jar.asm.Label[] bbLabels = new net.bytebuddy.jar.asm.Label[labels.length];
            for (int i = 0; i < labels.length; i++) {
                bbLabels[i] = convertLabel(labels[i]);
            }
            target.visitLookupSwitchInsn(bbDflt, keys, bbLabels);
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            target.visitMultiANewArrayInsn(descriptor, numDimensions);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            target.visitTryCatchBlock(
                    convertLabel(start),
                    convertLabel(end),
                    convertLabel(handler),
                    type
            );
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
            target.visitLocalVariable(
                    name,
                    descriptor,
                    signature,
                    convertLabel(start),
                    convertLabel(end),
                    index
            );
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            target.visitLineNumber(line, convertLabel(start));
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // Skip - handled by caller
        }

        @Override
        public void visitEnd() {
            // Skip - handled by caller
        }

        private final Map<Label, net.bytebuddy.jar.asm.Label> labelCache = new HashMap<>();

        private net.bytebuddy.jar.asm.Label convertLabel(Label label) {
            return labelCache.computeIfAbsent(label, k -> new net.bytebuddy.jar.asm.Label());
        }

        private Object convertConstant(Object value) {
            if (value instanceof Type type) {
                return net.bytebuddy.jar.asm.Type.getType(type.getDescriptor());
            } else if (value instanceof Handle handle) {
                return convertHandle(handle);
            }
            return value;
        }

        private net.bytebuddy.jar.asm.Handle convertHandle(Handle handle) {
            return new net.bytebuddy.jar.asm.Handle(
                    handle.getTag(),
                    handle.getOwner(),
                    handle.getName(),
                    handle.getDesc(),
                    handle.isInterface()
            );
        }

        private Object[] convertBootstrapArgs(Object[] args) {
            Object[] result = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                result[i] = convertConstant(args[i]);
            }
            return result;
        }
    }
}
