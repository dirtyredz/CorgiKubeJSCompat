# Decisions

## 2026-10-25 - Compile-only stubs for hosted CI

The owner approved mocking the pinned API when local jars are unavailable. Real mode still
hash-checks KubeJS, CorgiLib and Rhino; explicit or partial local inputs never silently fall back.
Five minimal types under src/stubs preserve the used descriptors and CONDITION's generic
signature, derived with javap -s -p from verified jars. Condition is a supporting interface
for that signature. Unused APIs and runtime instrumentation targets are intentionally absent.

verifyStubFaithfulness compiles main twice into separate fresh directories using the same
Java toolchain/options and classpaths differing only by real jars versus the stubs jar.
It requires identical class sets and bytes. check runs it when real inputs exist; hosted
stub-only CI logs an explicit skip. The check proves this mod's compilation, not runtime
equivalence of mocks. Dependency upgrades require reinspection and this local gate.

Shared CI uses datagen=false and gametests=false: instrumentation requires an installed pack,
and no runGameTestServer suite exists. Declarative release checks replace inspect-jar.ps1,
reject dependency classes, mixins and agent manifests, and require metadata, discovery and
license content. Shared reproducible archives support exact release-byte comparisons.

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
