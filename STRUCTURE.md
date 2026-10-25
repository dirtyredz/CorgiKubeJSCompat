# Structure

## Layout

- `src/main/java/io/github/dirtyredz/corgikubejscompat/`: the two spec-defined entry points;
  minimal Forge registration and the KubeJS class-initialization barrier.
- `src/main/resources/`: exact-version Forge dependencies, both-side KubeJS discovery,
  and `pack.mcmeta` for Minecraft pack discovery.
- `src/test/`: cheap plugin-bytecode and startup-trace contract checks; no Minecraft bootstrap.
- `src/stubs/java/`: minimal compile-only external API; `.ci/mod.json` `jar.forbiddenEntries`
  enforces never packaged. `StubApiContractTest` and the attestation guard the signatures;
  these stubs are never used at runtime.
- `.ci/`: mod-specific release checks and canonical shared CI scripts.
- `.github/`: canonical hosted verification/release workflows and build setup action.
- `gradle/compile-stubs.gradle`: mod-local pins, real/stub selection, stub jar and pin verification.
- `gradle/verify-stub-faithfulness.gradle`: real/stub compilation comparison and attestation guard/tests.
- `gradle/stub-faithfulness.json`: committed proof bound to the stub source tree and pinned jar hashes.
- `gradle/verify-release-jar.gradle`: canonical declarative archive guard and regression fixtures.
- `src/gameTest/java/io/github/dirtyredz/corgikubejscompat/verification/agent/`: installed-class
  `StartupOrderingAgent` instrumentation. Kept outside main and outside the release jar.
- `src/gameTest/java/io/github/dirtyredz/corgikubejscompat/verification/hooks/`: bootstrap-visible
  `StartupOrderingHooks` latch coordination, root mutation tracing, snapshots, and invalid-test detection.
- `tools/verify-runtime.cjs`: copies the saved stage into disposable `run/` directories,
  starts one headless BelowNormal server, reloads, stops, and validates results.
- `tools/test-world/`, `gradle/*world.gradle`: synced shared world assets/helpers, not release inputs.
- `docs/`: decisions, gotchas, and verification protocol.
- `build.gradle`, `settings.gradle`, `gradle.properties`: standalone offline build and pinned inputs.
- `gradle/wrapper/`, `gradlew*`, `.gitattributes`, `.gitignore`: byte-identical workspace templates.

The production entry points intentionally retain the exact package paths in the approved spec.
No shared library or additional production abstraction is needed for this one responsibility.
