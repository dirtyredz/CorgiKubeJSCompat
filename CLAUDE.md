# CorgiKubeJSCompat

Read [STRUCTURE.md](STRUCTURE.md), [decisions](docs/DECISIONS.md), and
[gotchas](docs/GOTCHAS.md) before changing the barrier or verification.

Java 17, Forge 1.20.1 / 47.4.10, ForgeGradle 6.0.54, Gradle 8.13.
The mod version lives only in `gradle.properties`.

## Verification

- Fast gate: `./gradlew.bat --no-daemon test build verifyReleaseJar`.
  With no local jars this uses compile-only stubs. Add `-PdependencyDir=<installed-mods>`
  to use the three exact, hash-checked jars listed in `gradle/compile-stubs.gradle` and run the automatic
  byte-identical compilation check (`verifyStubFaithfulness`). Use `--offline` with cached inputs.
  The default directory is `run/dependencies`. No downloads or installs are needed when
  Forge, Gradle, Java 17 and JUnit are already cached. There is no separate lint task.
- Runtime harness build: `./gradlew --offline verificationAgentJar`.
- Named heavier check required for barrier changes:
  `node tools/verify-runtime.cjs <saved-server-stage> <java17.exe> <scenario>`.
  Run `control` first, then `writer-first`, `reader-first`, and `baseline`, in separate JVMs.
  `plain-base` and `plain-fixed` run without instrumentation for paired measurements.
  Set `COMPAT_WORLD` to the same saved world directory for all measurements.
  See [verification](docs/VERIFY.md) for acceptance and limitations.
- Shared files: run the workspace `tools/sync-mod-files.ps1 -Check -Mod <this-worktree>`.
- Jar inspection: `verifyReleaseJar` enforces `.ci/mod.json`; no separate manual checker.

Check processes before Gradle; only one heavyweight build/server at a time. Runtime
verification waits up to 45 minutes, uses BelowNormal priority, bounded latches and a
1,200-second process watchdog. Never launch a client. Never install into the pack's instances.
The harness writes only into this repo's ignored `run/` and `build/` directories.

Commit as `dirtyredz <dirtyredz@live.com>`. No push or release without owner authorization.
The workspace owns Docket items; the initial build's item status belongs to its orchestrator.
