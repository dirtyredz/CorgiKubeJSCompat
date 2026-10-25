# Gotchas

- Supported versions are deliberately exact: KubeJS 2001.6.5-build.26 and CorgiLib 4.0.3.5.
  Reinspect hook placement and every class-initializer write before widening them.
- `CONDITION` is a supplier field. Read the field, not `get()`, and do not call either `init()`
  method. The class initializer performs the root writes; the methods do broader codec work.
- The plugin can load before this mod's Forge constructor. Keep both constructors inert.
- This fixes one demonstrated conflicting path. The historical writer was never captured;
  neither static evidence nor successful boots prove universal protection.
- Preserve `maxThreads = -1` in the pack. If targeted verification fails, the exact fallback
  is `config/fml.toml: maxThreads = 1`, followed by packwiz rehash and verification by the pack owner.
  Prior server measurements showed about 4.56 seconds mean startup cost for that fallback.
- Runtime tests target installed SRG names, not a ForgeGradle development runtime. Every
  instrumentation site is counted and missing milestones/watchdog expiry fail the run.
- The installed/transformed `MinecraftServer.m_129861_` reload method has multiple return
  paths. Observe every returned future, while asserting exactly one matching method.
- Never ship `build/verification/` jars. Only `build/libs/` contains the reobfuscated mod.
- The workspace shared-file checker accepts worktrees with `-Mod <absolute-worktree-path>`.
- Stub mode is compilation only: it cannot run KubeJS, CorgiLib or the installed-server proofs.
  A supplied dependencyDir or any pinned filename in run/dependencies selects real mode;
  missing files and hash mismatches then fail closed. An empty default selects stubs.
  Stubs live in build/stubs, never build/libs; release guards reject their package paths.
  Stub or pin edits require a real-jar faithfulness run and committed attestation; hosted CI
  validates that evidence and cannot regenerate it without the pinned jars.
- The supplied evidence's `installed-jars.json` contains hashes, not instance paths. Its
  `stage/` sibling contains the verified installed jars and server libraries.
- Other server jobs can launch Java through arbitrary argument files. The runtime runner
  waits for all existing Java processes rather than guessing activity from Forge class names.
- Child-JVM tests use `java.home/bin/java`, adding `.exe` only on Windows. Keep platform
  naming covered even on Windows hosts so Linux CI does not fail before the probe starts.
- Installed `MappedRegistry` inserts a holder with `computeIfAbsent`, then assigns that
  identical holder again. Trace the no-op assignment separately from actual root mutations;
  three CorgiLib insertions are not six changes. Forge workers can also share thread names,
  so traces include thread IDs.
- The control's failed Forge startup can leave pack threads alive. Validate the CME in the
  hook, let it propagate to Forge's startup-failure log, then terminate that control JVM.
- Full-pack bootstrap can exceed eight minutes even without another JVM. The outer watchdog
  is calibrated from a completed boot; start the writer coordinator's 60-second deadline only
  once the initializer is held, not at agent premain before jar scanning and mod loading.
