# Verification

Use the commands in [CLAUDE.md](../CLAUDE.md). No network downloads are permitted.
The saved server stage is a read-only input; every run gets a private copy under `run/`.

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
Forced-order acceptance and paired timings remain pending below.
