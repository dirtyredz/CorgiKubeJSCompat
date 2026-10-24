package io.github.dirtyredz.corgikubejscompat;

import corgitaco.corgilib.core.CorgiLibRegistry;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.rhino.util.wrap.TypeWrappers;

import java.util.Objects;

/** Completes CorgiLib's root registrations before KubeJS creates its root iterator. */
public final class CorgiRegistryPlugin extends KubeJSPlugin {
    public CorgiRegistryPlugin() {}

    @Override
    public void registerTypeWrappers(ScriptType type, TypeWrappers wrappers) {
        // GETSTATIC initializes the class or waits for its initializing thread, publishing
        // all three root writes. Do not invoke the supplier or the codec init methods.
        Objects.requireNonNull(CorgiLibRegistry.CONDITION, "CorgiLib condition registry supplier");
    }
}
