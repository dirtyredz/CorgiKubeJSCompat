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
- The workspace shared-file checker discovers `mods/*/build.gradle` and does not see worktrees.
  No hardcoded mod list needs editing; compare canonical bytes directly in a fresh worktree.
- The supplied evidence's `installed-jars.json` contains hashes, not instance paths. Its
  `stage/` sibling contains the verified installed jars and server libraries.
