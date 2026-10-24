# Structure

## Layout

- `src/main/java/io/github/dirtyredz/corgikubejscompat/`: the two spec-defined entry points;
  minimal Forge registration and the KubeJS class-initialization barrier.
- `src/main/resources/`: exact-version Forge dependencies and both-side KubeJS discovery.
- `src/test/`: cheap plugin-bytecode and startup-trace contract checks; no Minecraft bootstrap.
- `src/gameTest/java/io/github/dirtyredz/corgikubejscompat/verification/agent/`: installed-class
  `StartupOrderingAgent` instrumentation. Kept outside main and outside the release jar.
- `src/gameTest/java/io/github/dirtyredz/corgikubejscompat/verification/hooks/`: bootstrap-visible
  `StartupOrderingHooks` latch coordination, root mutation tracing, snapshots, and invalid-test detection.
- `tools/verify-runtime.cjs`: copies the saved stage into disposable `run/` directories,
  starts one headless BelowNormal server, reloads, stops, and validates results.
- `tools/inspect-jar.ps1`: asserts the release contents, metadata and plugin discovery; prints SHA-256.
- `docs/`: decisions, gotchas, and verification protocol.
- `build.gradle`, `settings.gradle`, `gradle.properties`: standalone offline build and pinned inputs.
- `gradle/wrapper/`, `gradlew*`, `.gitattributes`, `.gitignore`: byte-identical workspace templates.

The production entry points intentionally retain the exact package paths in the approved spec.
No shared library or additional production abstraction is needed for this one responsibility.
