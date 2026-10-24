# Corgi KubeJS Compat

A small Forge mod that completes CorgiLib's registry class initialization before KubeJS
iterates Minecraft's root registry. Parallel mod loading stays enabled.

Supports Minecraft **1.20.1**, **KubeJS 2001.6.5-build.26**, and **CorgiLib 4.0.3.5**.
Both dependencies are mandatory and restricted to those inspected versions on both sides.
Install alongside them on the server and client. The mod has no configuration and no mixins.

The KubeJS Java plugin reads `CorgiLibRegistry.CONDITION` in `registerTypeWrappers` for
every script type. JVM class initialization either completes there or waits for the writer.
It does not invoke the codec registration methods and does not depend on Forge constructor order.
This protects the identified CorgiLib writer, not every possible mod that might mutate the root.

Build and verify with [CLAUDE.md](CLAUDE.md) and [docs/VERIFY.md](docs/VERIFY.md).
Release jars are under `build/libs/`; verification agents are separate under `build/verification/`.
No dependencies or test classes are bundled in the mod jar.

The pack owner handles publication and packwiz metadata. Keep `maxThreads = -1` while
validating the fix; the fallback if it fails is `config/fml.toml: maxThreads = 1`.
