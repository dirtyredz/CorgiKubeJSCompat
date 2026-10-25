---
id: dk-fcf76be6
type: task
created: 2026-10-25
status: todo
since: 2026-10-25
area: ci
priority: P1
rank: n
parent:
fixes: []
blocked_by: []
relates: []
---
# Stub attestation proves consistency, not provenance

Corgi sign-off 2026-10-25 (Sol). gradle/verify-stub-faithfulness.gradle:28: gradle/stub-faithfulness.json holds repository-derived hashes, a literal result and a timestamp; stub-only hosted CI can't prove it came from a real-jar comparison, and it omits main sources/comparison inputs. Options: bind main sources + comparison inputs into the record; sign it, or run the real/stub comparison on a trusted private runner (e.g. ANU, which can hold the pinned jars). Accepted risk for now: sole developer.
