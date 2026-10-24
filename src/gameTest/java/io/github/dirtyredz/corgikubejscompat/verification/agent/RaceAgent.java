package io.github.dirtyredz.corgikubejscompat.verification.agent;

import io.github.dirtyredz.corgikubejscompat.verification.hooks.RaceHooks;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Set;

/** Test-only load-time instrumentation of the installed, SRG-named classes. */
public final class RaceAgent implements ClassFileTransformer {
    private static final String HOOKS = "io/github/dirtyredz/corgikubejscompat/verification/hooks/RaceHooks";
    private static final String REGISTRY = "corgitaco/corgilib/core/CorgiLibRegistry";
    private static final String CONSTRUCTOR = "corgitaco/corgilib/forge/CorgiLibForge";
    private static final String PLUGIN = "io/github/dirtyredz/corgikubejscompat/CorgiRegistryPlugin";
    private static final String MANAGER = "dev/latvian/mods/kubejs/script/ScriptManager";
    private static final String MAPPED = "net/minecraft/core/MappedRegistry";
    private static final String SERVER = "net/minecraft/server/MinecraftServer";
    private static final Set<String> TARGETS = Set.of(REGISTRY, CONSTRUCTOR, PLUGIN, MANAGER, MAPPED, SERVER);

    public static void premain(String args, Instrumentation instrumentation) {
        RaceHooks.start();
        instrumentation.addTransformer(new RaceAgent());
    }

    private static MethodInsnNode call(String name, String descriptor) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, descriptor, false);
    }

    @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                                      ProtectionDomain domain, byte[] bytes) {
        if (name == null || !TARGETS.contains(name)) return null;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int changes = 0;
            for (MethodNode method : node.methods) {
                if (name.equals(SERVER) && method.name.equals("m_129861_")
                        && method.desc.equals("(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;")) {
                    int returns = 0;
                    for (var i : method.instructions.toArray()) if (i.getOpcode() == Opcodes.ARETURN) {
                        method.instructions.insertBefore(i, call("observeReload",
                                "(Ljava/util/concurrent/CompletableFuture;)Ljava/util/concurrent/CompletableFuture;"));
                        returns++;
                    }
                    if (returns == 0) throw new IllegalStateException("Reload method has no return");
                    changes++;
                }
                if (name.equals(REGISTRY) && method.name.equals("<clinit>")) {
                    method.instructions.insert(call("initializerEnter", "()V"));
                    for (var i : method.instructions.toArray()) if (i.getOpcode() == Opcodes.RETURN)
                        method.instructions.insertBefore(i, call("initializerEnd", "()V"));
                    changes++;
                }
                if (name.equals(CONSTRUCTOR) && method.name.equals("<init>")) {
                    for (var i : method.instructions.toArray()) {
                        if (i instanceof MethodInsnNode m && m.owner.equals("corgitaco/corgilib/CorgiLib") && m.name.equals("init")) {
                            method.instructions.insertBefore(i, call("constructorEnter", "()V"));
                            method.instructions.insert(i, call("constructorInitialized", "()V"));
                            changes++;
                        }
                    }
                }
                if (name.equals(PLUGIN) && method.name.equals("registerTypeWrappers")) {
                    method.instructions.insert(call("hookEnter", "()V"));
                    for (var i : method.instructions.toArray()) if (i.getOpcode() == Opcodes.RETURN)
                        method.instructions.insertBefore(i, call("hookReturn", "()V"));
                    changes++;
                }
                if (name.equals(MAPPED) && method.name.equals("<init>")) {
                    for (var i : method.instructions.toArray()) {
                        if (i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD && f.name.equals("f_205842_")) {
                            InsnList wrap = new InsnList();
                            wrap.add(new VarInsnNode(Opcodes.ALOAD, 1)); // constructor's ResourceKey
                            wrap.add(call("traceMap", "(Ljava/util/Map;Ljava/lang/Object;)Ljava/util/Map;"));
                            method.instructions.insertBefore(i, wrap);
                            changes++;
                        }
                    }
                }
                if (name.equals(MANAGER) && method.name.equals("load") && method.desc.equals("()V")) {
                    boolean rootKeys = false;
                    for (var i : method.instructions.toArray()) {
                        if (i.getOpcode() == Opcodes.RETURN) {
                            InsnList snapshot = new InsnList();
                            snapshot.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            snapshot.add(call("scriptsLoaded", "(Ljava/lang/Object;)V"));
                            method.instructions.insertBefore(i, snapshot);
                        }
                        if (i instanceof MethodInsnNode m && m.name.equals("m_214010_")) rootKeys = true;
                        else if (rootKeys && i instanceof MethodInsnNode m && m.owner.equals("java/util/Set") && m.name.equals("iterator")) {
                            method.instructions.set(i, call("rootIterator", "(Ljava/util/Set;)Ljava/util/Iterator;"));
                            rootKeys = false;
                            changes++;
                        }
                    }
                }
            }
            if (changes != 1) throw new IllegalStateException("Expected one instrumentation site in " + name + ", got " + changes);
            RaceHooks.event("TRANSFORM " + name);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable failure) {
            RaceHooks.invalid("Instrumentation failed: " + name, failure);
            return null; // invalid() halts; transformer exceptions alone are ignored by the JVM.
        }
    }
}
