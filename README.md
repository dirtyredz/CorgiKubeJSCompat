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

Keep `maxThreads = -1` while
validating the fix; the fallback if it fails is `config/fml.toml: maxThreads = 1`.

## Release

The version lives only in `gradle.properties`. Prepare `docs/releases/<version>.md` for a
new release, then obtain owner authorization to push. Push CI runs
`./gradlew test build verifyReleaseJar` and publishes nothing. After green main CI and
owner authorization, dispatch `gh workflow run release.yml --ref main`. The shared workflow
verifies, builds, uploads a draft and checks downloaded SHA-256s before publishing the jar,
its `.sha256`, and `corgikubejscompat.pw.toml` for the pack session. Published bytes are never
replaced; versions starting with `0.` or containing `-` are prereleases.

Local gate: `./gradlew.bat --no-daemon test build verifyReleaseJar`. Without local inputs,
CI compiles against isolated stubs; actual installations still require both real mods.
With `-PdependencyDir=<installed-mods>`, all three pinned API jars (including Rhino) are
hash-checked, and `verifyStubFaithfulness` requires byte-identical real/stub main classes.
That local gate writes a committed attestation; hosted CI rejects changed stubs or pins
until the real-jar gate regenerates it.
See [verification](docs/VERIFY.md) for the reproduction commands and inspected signatures.
CI declares no datagen and no Forge GameTests: the existing instrumentation proofs need
the real installed pack and are not a `runGameTestServer` suite.
`.ci/mod.json` owns release checks for dependency isolation, plugin discovery, exact BOTH-side
dependencies, license, and absence of mixin/agent activation.
