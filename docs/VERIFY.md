# Verification

Use the commands in [CLAUDE.md](../CLAUDE.md). Cached builds can use `--offline`.
The saved server stage is a read-only input; every run gets a private copy under `run/`.

## Compile stubs and hosted CI

Run `./gradlew.bat --no-daemon test build verifyReleaseJar` with no `dependencyDir` and
no pinned jar filenames under `run/dependencies` to reproduce hosted stub mode. Nothing
outside the checkout is searched automatically. For real mode, append
`"-PdependencyDir=C:/Users/dirty/MultiMC/instances/Evil Eye Local/.minecraft/mods"` (read only).
An explicit directory or any default pinned file requires all three correct SHA-256 pins.

`./gradlew.bat --no-daemon verifyStubFaithfulness "-PdependencyDir=<installed-mods>"`
compiles main against each API in separate `build/faithfulness/{real,stubs}` directories,
then compares the complete class path sets and each byte. This also runs as part of `check`
in real mode; stub-only CI prints `SKIP verifyStubFaithfulness: pinned real jars unavailable
(CI uses compile-only stubs).` Archive timestamps/order are normalized by the shared guard.
No runtime proof is implied by successful compilation.

The inspected inputs (SHA-256, also enforced in build.gradle) are:

| Jar | SHA-256 |
|---|---|
| kubejs-forge-2001.6.5-build.26.jar | `1769312192fbf9d72f45054ba61130523bfb471b5f480a4bff8c210ec09400bb` |
| Corgilib-Forge-1.20.1-4.0.3.5.jar | `6879c214e0793278e14105b70d085ba218551c39daa78c846273030278bef350` |
| rhino-forge-2001.2.3-build.10.jar | `fed2211429301bf043864183cab9ab8e92d4cc4dbb9e488ce6c75217c54584a6` |

Reinspect with `javap -s -p -classpath "<kubejs.jar>;<corgilib.jar>;<rhino.jar>"`
and these fully qualified types before changing the pins:

- `corgitaco.corgilib.core.CorgiLibRegistry`: public class, public static final
  `Supplier<Registry<Codec<? extends corgitaco.corgilib.entity.condition.Condition>>> CONDITION`;
  descriptor `Ljava/util/function/Supplier;`. Supporting `Condition` is an interface.
- `dev.latvian.mods.kubejs.KubeJSPlugin`: public class, public constructor `()V`, public
  instance `registerTypeWrappers(ScriptType, TypeWrappers)` with descriptor
  `(Ldev/latvian/mods/kubejs/script/ScriptType;Ldev/latvian/mods/rhino/util/wrap/TypeWrappers;)V`.
- `dev.latvian.mods.kubejs.script.ScriptType`: enum, not an ordinary class or interface.
- `dev.latvian.mods.rhino.util.wrap.TypeWrappers`: public non-final class, no generics.

Only APIs consumed by main are represented. ScriptType's unused interfaces/methods and
Condition's unused members are omitted. Instrumentation string targets (`CorgiLibForge`,
`CorgiLib.init`, `ScriptManager`, Minecraft registry reflection) resolve only in real pack
processes, so they get no compile mocks. There are no mixin targets.
StubApiContractTest locks the consumed signatures; PluginContractTest locks production bytecode.
`verifyReleaseJar` tests synthetic contaminated archives and checks `.ci/mod.json`, replacing
the old `tools/inspect-jar.ps1` manual checker. Historical results below retain its old command.

## Installed-server verification protocol

The following runtime protocol is historical qualification, not the hosted CI gate.

1. `control`: no compatibility jar. Hold the registry initializer, create KubeJS's actual
   root iterator, release the writer, wait for its constructor's init call to return, resume.
   Require `ConcurrentModificationException` at `ScriptManager.java:192`, three overlapping writes,
   and `PASS control` in the trace. The expected failed server boot is not a successful startup.
   After Forge reports that failure, terminate lingering pack threads; record `controlStopped`.
2. `writer-first`: hold initialization; enter the actual plugin hook; independent coordinator
   releases the writer. Require initializer end, hook return, then iterator creation.
3. `reader-first`: hold the constructor before init; let the plugin initialize; release the
   constructor after hook return. Require precisely one insertion for each of the three keys.
4. `baseline`: tracing with no compatibility jar and no forced interleaving. Compare the final
   registry snapshot byte-for-byte with both fixed runs. Fixed runs and baseline must start,
   reload, stop normally, have all three nonempty CorgiLib codec registries and no overlapping writes.

The root key map is wrapped before its first use. Puts, removals, entry changes and mutations
through collection views log keys, threads and stacks, including vanilla bootstrap writes.
An identical holder assignment is logged as `ROOT_PUT_UNCHANGED`, not a second mutation.
The cheap trace test covers the installed registry's insert-then-identical-assignment sequence
and preserves the backing HashMap's fail-fast iterator behavior.
Instrumentation changes only test processes. Tracing itself changes timing; do not benchmark it.
All waits have a 60-second bound; the outer process watchdog is 1,200 seconds. Exit 86 or missing
required events is invalid, never evidence that the fix worked.

For paired timing, set `COMPAT_WORLD` to one cached world and alternate `plain-base` and
`plain-fixed` three times. Each gets identical copies and default parallel loading, with no
agent. Compare JVM start-to-Done medians; the spec suggests at most one second overhead.
Record background activity and avoid interpreting noisy timings as a precise causal estimate.
The owner verifies clients separately; never launch a visible client from an agent.

Inspect the release jar for exact mandatory BOTH dependencies, its single plugin entry,
and no `verification`, test, agent or dependency classes. Compute SHA-256 on the final
reobfuscated jar. Keep runtime logs and snapshots in ignored run directories for handoff.

## Initial-build verification, 2026-10-24

The implementation and cheap gate pass; **runtime acceptance is incomplete**.
Inputs were the saved `2026-10-24-kubejs-cme/stage` and cached Adoptium Java 17.
`COMPAT_WORLD` pointed to that evidence folder's `runs/default-1/smoke-world`.

- `./gradlew --offline test build verificationAgentJar -PdependencyDir=<stage>/mods`:
  passed, three tests. The new trace regression first failed with
  `Three insertions, not six mutations ==> expected: <3> but was: <6>`, then passed
  after separating identical holder assignments from mutations.
- `powershell -NoProfile -File tools/inspect-jar.ps1`: passed; only the two production
  classes, exact BOTH dependencies and the single plugin entry. Jar SHA-256:
  `a53230f6087654395a88bf03331b7ebad092a0061a7dd1a262b0a8730276e4f1`.
- Workspace `sync-mod-files.ps1 -Check` and direct comparison of all six canonical
  worktree files passed. `node --check tools/verify-runtime.cjs` and `git diff --check` passed.
- First control, `run/control-1792863939369`: reproduced the original CME at
  `ScriptManager.java:192`, but was **invalid**: duplicate unchanged assignments were
  counted as mutations, and the failed server left threads alive until the watchdog.
  Both harness issues were corrected and the counting regression is covered by the cheap gate.
- Corrected control, `run/control-1792866770732`: **invalid**, `expired: true` at 480 seconds,
  before any ordering hook. `build/verification/control-thread-dump.txt` shows the main thread
  running in Forge's jar-package scan (`Jar.getPackages`), not waiting on a barrier latch.
  Other Java jobs started after this server launched; a valid exclusive run could not be completed.

Both controls reported `Runtime verification invalid or failed; inspect <run>`.
Writer-first, reader-first, baseline/snapshot comparison, and paired uninstrumented timing
are **not run**: resume with a valid control in an exclusive machine window, then follow the
protocol above. Client checks remain with the owner. No pack instance or Docket status was changed.

## Runtime acceptance continuation, 2026-10-24

The release plugin is unchanged. Runtime commands use these read-only inputs:

```powershell
$stage = 'C:/Users/dirty/projects/artifacts/evil-eye-pack/jobs/2026-10-24-kubejs-cme/stage'
$java = 'C:/Users/dirty/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2/bin/java.exe'
$env:COMPAT_WORLD = 'C:/Users/dirty/projects/artifacts/evil-eye-pack/jobs/2026-10-24-kubejs-cme/runs/default-1/smoke-world'
node tools/verify-runtime.cjs $stage $java control
```

The first continuation control, `run/control-1792894310521`, again expired at 480 s.
Verbatim result and error (stack frames omitted):

```text
{"scenario":"control","run":"C:\\Users\\dirty\\projects\\minecraft-mods\\.worktrees\\corgikubejscompat\\run\\control-1792894310521","pid":14620,"code":null,"expired":true,"controlStopped":false,"reloaded":false,"passed":false}
Error: Runtime verification invalid or failed; inspect C:\Users\dirty\projects\minecraft-mods\.worktrees\corgikubejscompat\run\control-1792894310521
```

`Get-CimInstance Win32_Process` found no Java process before launch. Another checkout's
Gradle job began during the run. Dumps in `build/verification/resume-control-thread-dump*.txt`
show SecureJar construction at 100.78 s and Log4j setup at 411.89 s, not barrier waits.
Forge logging began about 457 s after launch.

To measure a complete boot, an ignored copy of the runner,
`build/verification/measure-runtime.cjs`, used a finite diagnostic cap of 1,800 s:
`node build/verification/measure-runtime.cjs $stage $java plain-base`.
Its first run, `run/plain-base-1792894818477`, was stopped when another Gradle build
became active; it is invalid, with no completed boot measurement. Its competing-process
snapshot is `process-check.json`; its dump is `build/verification/diagnostic-plain-thread-dump.txt`.
After the runner waited for the other build to exit, the retry completed startup, reload,
and normal shutdown. Sampled process checks during the retry found only its own JVM.
Verbatim output:

```text
Done in 554.788 seconds; requesting reload
{"scenario":"plain-base","run":"C:\\Users\\dirty\\projects\\minecraft-mods\\.worktrees\\corgikubejscompat\\run\\plain-base-1792895374019","pid":23456,"code":0,"expired":false,"controlStopped":false,"doneMs":554787.9354000001,"reloaded":true,"passed":true}
```

The measured boot exceeds 480 s. The runner now allows 1,200 s: twice 554.788 s,
rounded up to the next minute (1,140 s), plus 60 s for reload/shutdown. This is a
finite 645.212 s margin over the measured startup. Latch bounds remain 60 s.
The diagnostic is not included in the paired performance samples. Background antivirus
and desktop activity were observed; quiet Java checks alone do not eliminate timing noise.
The unpatched pack also reports three failed recipes; these are outside this mod's scope.

`powershell -NoProfile -File tools/inspect-jar.ps1` passed, verbatim:

```text
PASS: exact BOTH dependencies, plugin entry, two production classes, no tests or bundled dependencies
```

SHA-256 remains `a53230f6087654395a88bf03331b7ebad092a0061a7dd1a262b0a8730276e4f1`.
`node --check tools/verify-runtime.cjs` and `git diff --check` passed after the timeout edit.
### Forced ordering and coordinator correction

The next control, `run/control-1792896244903`, was interrupted after another checkout
started a Gradle build. Its `process-check.json` records the overlap. Verbatim result:

```text
{"scenario":"control","run":"C:\\Users\\dirty\\projects\\minecraft-mods\\.worktrees\\corgikubejscompat\\run\\control-1792896244903","pid":30988,"code":4294967295,"expired":false,"controlStopped":false,"reloaded":false,"passed":false}
Error: Runtime verification invalid or failed; inspect C:\Users\dirty\projects\minecraft-mods\.worktrees\corgikubejscompat\run\control-1792896244903
```

After waiting for that build to exit, `node tools/verify-runtime.cjs $stage $java control`
completed. Its trace contains `ScriptManager.load(ScriptManager.java:192)`, exactly one
insertion for each CorgiLib key, and `enumerations=1 overlap=3`. Verbatim result:

```text
{"scenario":"control","run":"C:\\Users\\dirty\\projects\\minecraft-mods\\.worktrees\\corgikubejscompat\\run\\control-1792896787854","pid":23304,"code":null,"expired":false,"controlStopped":true,"reloaded":false,"passed":true}
```

The first `writer-first` exposed a harness-only deadline defect: the independent coordinator
started its 60-second wait for the writer at premain, before the full pack could bootstrap.
Verbatim trace and runner result:

```text
395708058169900 [compat-latch-coordinator#14] INVALID Watchdog expired: writer held
{"scenario":"writer-first","run":"C:\\Users\\dirty\\projects\\minecraft-mods\\.worktrees\\corgikubejscompat\\run\\writer-first-1792897441407","pid":33176,"code":86,"expired":false,"controlStopped":false,"reloaded":false,"passed":false}
Error: Runtime verification invalid or failed; inspect C:\Users\dirty\projects\minecraft-mods\.worktrees\corgikubejscompat\run\writer-first-1792897441407
```

`StartupOrderingCoordinatorTest` reproduced the defect in an isolated JVM before the fix:

```text
Writer deadline started before the initializer was held
 ==> expected: <0> but was: <1>
4 tests completed, 1 failed
Execution failed for task ':test'.
BUILD FAILED in 3m 58s
```

The coordinator now starts when the writer initializer is held; all interleaving waits retain
their 60-second bounds. No production code changed. The documented fast gate plus agent build,
`./gradlew --offline test build verificationAgentJar -PdependencyDir=<stage>/mods`, then passed
all four tests: `BUILD SUCCESSFUL in 59s`. No separate lint task exists.
The build regenerated ZIP metadata in the release jar; comparison verified every entry's
contents byte-for-byte against the original. The original hash-checked release jar was restored
from the first writer-first run before continuing acceptance. Jar inspection passed again with
the original SHA-256 above.

The corrected `writer-first`, `reader-first`, and `baseline` commands all passed. Verbatim
runner results (the existing runner serializes its truthy `passed` value as the contents hash):

```text
{"scenario":"writer-first","run":"C:\\Users\\dirty\\projects\\minecraft-mods\\.worktrees\\corgikubejscompat\\run\\writer-first-1792898021609","pid":37920,"code":0,"expired":false,"controlStopped":false,"doneMs":526663.9585,"reloaded":true,"contentsHash":"2cc1613fb6225f6d2438648a4e98f38ea14a22e574d0396ab0da2ddac0bf91d6","passed":"2cc1613fb6225f6d2438648a4e98f38ea14a22e574d0396ab0da2ddac0bf91d6"}
{"scenario":"reader-first","run":"C:\\Users\\dirty\\projects\\minecraft-mods\\.worktrees\\corgikubejscompat\\run\\reader-first-1792898643784","pid":26740,"code":0,"expired":false,"controlStopped":false,"doneMs":391061.9915,"reloaded":true,"contentsHash":"2cc1613fb6225f6d2438648a4e98f38ea14a22e574d0396ab0da2ddac0bf91d6","passed":"2cc1613fb6225f6d2438648a4e98f38ea14a22e574d0396ab0da2ddac0bf91d6"}
{"scenario":"baseline","run":"C:\\Users\\dirty\\projects\\minecraft-mods\\.worktrees\\corgikubejscompat\\run\\baseline-1792899135694","pid":36592,"code":0,"expired":false,"controlStopped":false,"doneMs":392432.6631,"reloaded":true,"contentsHash":"2cc1613fb6225f6d2438648a4e98f38ea14a22e574d0396ab0da2ddac0bf91d6","passed":"2cc1613fb6225f6d2438648a4e98f38ea14a22e574d0396ab0da2ddac0bf91d6"}
```

Writer-first recorded `HOOK_WAITING_ON_INITIALIZATION`, `INDEPENDENT_WRITER_RELEASE`,
then `INITIALIZER_END`, `HOOK_RETURN`, and `ITERATOR_CREATED 1` in that order.
Reader-first recorded its held constructor before the hook, initialization on the hook's
thread, hook return, iterator creation, then completion of the released constructor.
Every successful instrumented trace ended with `enumerations=3 overlap=0` and exactly one
insertion for each of `blending_function`, `condition`, and `villager_trades_item_listing`.
All three codec registries were nonempty and unchanged across reload.

A direct byte comparison of the three `registry-snapshot-3.txt` files passed:

```text
PASS: baseline, writer-first and reader-first final snapshots are byte-identical
```

The first comparison command had a PowerShell/Node inline quoting error,
`SyntaxError: missing ) after argument list`; it was corrected using PowerShell byte reads
and exact Base64 equality. The hashes above independently agree with that byte comparison.
Workspace `sync-mod-files.ps1 -Check` returned `OK 5 mods in sync`; direct SHA-256 comparison
of all six worktree canonical files returned `PASS: all six worktree canonical files match`.
Paired uninstrumented timings remain pending.

## CI adoption verification, 2026-10-25

- `./gradlew.bat --no-daemon test build verifyReleaseJar` with no dependencyDir and no
  run/dependencies: `BUILD SUCCESSFUL in 1m 31s`. Six Java tests passed (the final gate reused
  their up-to-date results). The faithful-compilation task logged its explicit CI skip.
- `./gradlew.bat --no-daemon test build verifyReleaseJar verifyStubFaithfulness
  "-PdependencyDir=C:/Users/dirty/MultiMC/instances/Evil Eye Local/.minecraft/mods"`:
  `BUILD SUCCESSFUL in 1m 48s`; `PASS stub faithfulness: 2 main class files are byte-identical`.
  All three real jar hashes matched the pins. Both gates passed all synthetic release-guard cases.
- Direct byte comparison also passed for the complete real/stub reobfuscated release jars.
  Both SHA-256 values: `e3b211f5b7603f65d737260c06c247cba67543397e4309d031e77c9c0979d263`.
- Root `node --test tools/ci-test/*.test.mjs`: 13 passed, 0 failed.
  `node tools/test-sync-mod-files.mjs`: passed. Canonical guard and its synthetic fixtures
  are committed in root branch corgi-stub-guard, commit `ba739a9`.
- Root worktree `tools/sync-mod-files.ps1 -Check -Mod <this-worktree>`: `OK 1 mods in sync`.
  `git diff --check` in both worktrees and `dk check` passed.
- `gh release list -R dirtyredz/CorgiKubeJSCompat` reported v1.0.1 already published
  (2026-10-25T05:41:42Z). No version change or new 1.0.1 notes were made.

Intermediate failures, corrected before the successful gates:

```text
StubApiContractTest > pluginCallbackAndParameterKindsMatchPinnedApi() FAILED
    java.lang.NullPointerException at StubApiContractTest.java:17
StubApiContractTest > conditionRetainsItsRealFieldDescriptorAndGenericSignature() FAILED
    java.lang.NullPointerException at StubApiContractTest.java:17
6 tests completed, 2 failed
```

The tests were added after that invocation had configured Gradle, so it lacked their new
stubClasses property. A fresh invocation picked up the complete configuration and passed.
The negative jar fixture then exposed Ant's Windows path separator handling:

```text
Execution failed for task ':testReleaseJarVerifier'.
> Jar guard regression forbiddenMixin: contamination accepted
```

Normalize ZIP paths and patterns to the platform separator before Ant matching; the same
fixture then rejected the root-level mixin file. Runtime pack proofs and Forge GameTests were
not run: production code is unchanged, and there is no standalone Forge GameTest suite.
