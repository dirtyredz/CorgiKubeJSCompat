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
All waits have a 60-second bound; the outer process watchdog is 480 seconds. Exit 86 or missing
required events is invalid, never evidence that the fix worked.

For paired timing, set `COMPAT_WORLD` to one cached world and alternate `plain-base` and
`plain-fixed` three times. Each gets identical copies and default parallel loading, with no
agent. Compare JVM start-to-Done medians; the spec suggests at most one second overhead.
Record background activity and avoid interpreting noisy timings as a precise causal estimate.
The owner verifies clients separately; never launch a visible client from an agent.

Inspect the release jar for exact mandatory BOTH dependencies, its single plugin entry,
and no `verification`, test, agent or dependency classes. Compute SHA-256 on the final
reobfuscated jar. Keep runtime logs and snapshots in ignored run directories for handoff.
