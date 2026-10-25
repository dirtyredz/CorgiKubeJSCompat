package dev.latvian.mods.kubejs;

import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.rhino.util.wrap.TypeWrappers;

/** Only the constructor and callback used by this mod, as inspected with javap. */
public class KubeJSPlugin {
    public KubeJSPlugin() {}
    public void registerTypeWrappers(ScriptType type, TypeWrappers wrappers) {}
}
