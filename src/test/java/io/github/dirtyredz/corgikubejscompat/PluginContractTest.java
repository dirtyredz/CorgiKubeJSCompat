package io.github.dirtyredz.corgikubejscompat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Inspect the actual compiled plugin without bootstrapping Minecraft in a unit JVM. */
class PluginContractTest {
    private ClassNode plugin() throws Exception {
        Path classes = Path.of(System.getProperty("mainClasses"));
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(classes.resolve(
                "io/github/dirtyredz/corgikubejscompat/CorgiRegistryPlugin.class"))).accept(node, 0);
        return node;
    }

    @Test void callbackHasUnconditionalFieldBarrierAndDoesNotRegisterCodecs() throws Exception {
        ClassNode node = plugin();
        assertEquals("dev/latvian/mods/kubejs/KubeJSPlugin", node.superName);
        MethodNode hook = node.methods.stream().filter(m -> m.name.equals("registerTypeWrappers")).findFirst().orElseThrow();
        assertEquals("(Ldev/latvian/mods/kubejs/script/ScriptType;Ldev/latvian/mods/rhino/util/wrap/TypeWrappers;)V", hook.desc);
        var code = Arrays.stream(hook.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList();
        FieldInsnNode read = assertInstanceOf(FieldInsnNode.class, code.get(0));
        assertEquals(Opcodes.GETSTATIC, read.getOpcode());
        assertEquals("corgitaco/corgilib/core/CorgiLibRegistry", read.owner);
        assertEquals("CONDITION", read.name);
        assertTrue(code.stream().anyMatch(i -> i instanceof MethodInsnNode m
                && m.owner.equals("java/util/Objects") && m.name.equals("requireNonNull")));
        assertFalse(code.stream().anyMatch(i -> i instanceof JumpInsnNode));
        assertTrue(hook.tryCatchBlocks.isEmpty(), "Initialization failures must propagate");
        for (MethodNode method : node.methods) {
            for (var i : method.instructions) {
                if (i instanceof MethodInsnNode m) assertFalse(m.owner.startsWith("corgitaco/"), "Never call init() or codecs");
            }
        }
    }

    @Test void publicConstructorIsInertAndDiscoveryIsBothSides() throws Exception {
        MethodNode ctor = plugin().methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        assertEquals("()V", ctor.desc);
        assertTrue((ctor.access & Opcodes.ACC_PUBLIC) != 0);
        assertEquals(3, Arrays.stream(ctor.instructions.toArray()).filter(i -> i.getOpcode() >= 0).count());
        String discovery = Files.readString(Path.of(System.getProperty("mainResources"), "kubejs.plugins.txt")).trim();
        assertEquals("io.github.dirtyredz.corgikubejscompat.CorgiRegistryPlugin", discovery);
    }
}
