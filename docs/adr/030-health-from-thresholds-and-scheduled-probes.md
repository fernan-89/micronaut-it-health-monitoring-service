# ADR-030: Health Comes From Thresholds, and Probes Are Scheduled With a Lease

## Status
Accepted

## Context
One dropped packet is not an outage, and an outage reported on every blip teaches people to ignore it. Several instances of the service may run at once and must not probe the same target at the same instant.

## Decision
- A `HealthCheck` has two independent dimensions: the lifecycle `status` (`ACTIVE` or `PAUSED`: is it probed on its own) and the observed `health` (`UNKNOWN`, `UP`, `DOWN`).
- **Health changes only when a threshold is crossed**: `DOWN` after `failureThreshold` failures in a row (default 3), `UP` after `successThreshold` successes in a row (default 1). A success resets the failure count and the other way round. Counters are capped, so they never overflow.
- Only a **change of health** and the staff actions (`INITIATED`, `UPDATED`, `PAUSED`, `RESUMED`) enter the audit trail, never every probe; the trail keeps its last 200 entries, so a flapping check cannot grow it for ever. Every probe is kept separately in an expiring history (ADR-032).
- A new target is a different thing: updating the target starts the health again from `UNKNOWN` and makes the check due at once. The type (HTTP or TCP) cannot change.
- **Scheduling.** A scheduler ticks every few seconds and **claims** the ACTIVE checks that are due, one atomic find-and-update each, which also **leases** the check (it is not due again for 60 seconds, longer than the longest probe, unless its result is recorded sooner). Two instances therefore never take the same check. When a result is recorded the check is next due one interval later; a paused check keeps no schedule, and resuming makes it due at once. A manual run (`check/execute`) works whatever the status and goes through the same recording.
- A round probes up to `claim-batch` checks, `concurrency` at once; one check failing never stops the round, and a round still running when the next tick comes is not doubled.

## Consequences
- Positive: no alert noise from a single failure, safe with several instances, bounded work per tick.
- Negative: a real outage is seen after `failureThreshold` intervals (a trade the operator tunes per check); a check being probed when the process dies stays leased for up to a minute.
