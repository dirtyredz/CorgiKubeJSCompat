# Decisions

## Use the KubeJS hook and JVM initialization barrier

The inspected KubeJS hook runs at `ScriptManager.java:185`, before the root loop at line 192.
An unconditional nonconstant read of `CorgiLibRegistry.CONDITION` forces or waits for all
three root insertions in its static initializer. Initialization failures propagate.
The public no-argument plugin constructor is inert. The Forge entry point has no ordering role.

Do not call `CorgiLib.init()` or `CorgiLibRegistry.init()`: those method bodies register codecs.
Do not copy the live key set, lock only the reader, or rely on Forge dependency sorting.
Those alternatives do not coordinate with the writer.

## Pin the inspected versions; compile from verified offline inputs

Mandatory BOTH-side exact-version dependencies prevent silently extending this claim to
uninspected releases. The installed KubeJS, CorgiLib and Rhino jars are hash checked before
compilation and never bundled. Only mod API names and `Supplier` appear in production bytecode,
so no Minecraft member names in these dependencies need deobfuscation. The release jar still
passes ForgeGradle's usual reobfuscation task.

## Keep deterministic instrumentation out of production

Separate gameTest jars instrument the actual installed classes in fresh JVMs. Bounded latches
force the failing control and both fixed orders. An independent thread releases the held writer.
Root-map tracing preserves the underlying HashMap iterator. Missing milestones are invalid tests.
Normal starts without instrumentation measure cost separately; client validation belongs to the owner.
