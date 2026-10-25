package io.github.dirtyredz.corgikubejscompat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Pinned javap contracts, inspected without loading Minecraft or any mocked runtime. */
class StubApiContractTest {
    private ClassNode read(String name) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(System.getProperty("stubClasses"), name + ".class")))
                .accept(node, 0);
        return node;
    }

    @Test void conditionRetainsItsRealFieldDescriptorAndGenericSignature() throws Exception {
        var registry = read("corgitaco/corgilib/core/CorgiLibRegistry");
        assertEquals(0, registry.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_FINAL));
        var field = registry.fields.stream().filter(f -> f.name.equals("CONDITION")).findFirst().orElseThrow();
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, field.access);
        assertEquals("Ljava/util/function/Supplier;", field.desc);
        assertEquals("Ljava/util/function/Supplier<Lnet/minecraft/core/Registry<Lcom/mojang/serialization/Codec<"
                + "+Lcorgitaco/corgilib/entity/condition/Condition;>;>;>;", field.signature);
        assertNull(field.value, "The field must not become a compile-time constant");
        assertTrue((read("corgitaco/corgilib/entity/condition/Condition").access & Opcodes.ACC_INTERFACE) != 0);
    }

    @Test void pluginCallbackAndParameterKindsMatchPinnedApi() throws Exception {
        var plugin = read("dev/latvian/mods/kubejs/KubeJSPlugin");
        assertEquals(0, plugin.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_FINAL));
        var ctor = plugin.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        assertEquals("()V", ctor.desc);
        assertEquals(Opcodes.ACC_PUBLIC, ctor.access);
        var method = plugin.methods.stream().filter(m -> m.name.equals("registerTypeWrappers")).findFirst().orElseThrow();
        assertEquals(Opcodes.ACC_PUBLIC, method.access);
        assertEquals("(Ldev/latvian/mods/kubejs/script/ScriptType;Ldev/latvian/mods/rhino/util/wrap/TypeWrappers;)V", method.desc);
        var script = read("dev/latvian/mods/kubejs/script/ScriptType");
        assertTrue((script.access & Opcodes.ACC_ENUM) != 0);
        assertEquals("java/lang/Enum", script.superName);
        assertEquals(0, read("dev/latvian/mods/rhino/util/wrap/TypeWrappers").access
                & (Opcodes.ACC_INTERFACE | Opcodes.ACC_FINAL));
    }
}
