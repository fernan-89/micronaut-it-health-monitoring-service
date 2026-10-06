# ADR-033: Every Write Is Version-Guarded, and the Claim Is Atomic

## Status
Accepted

## Context
Staff edit a check while the scheduler records results into it, and several instances claim work at the same time.

## Decision
- A `HealthCheck` carries a `version`. A save is **one atomic update that applies only while the stored version is still the one loaded**, bumps it, and appends the audit entry (capped at the last 200) when there is one. A lost race is 409 `ERR-HLM-00409`: read again and retry.
- **Recording a probe result** loads the check fresh, applies the result and saves with that version, **retrying up to three times** when a person changed the check in between; after three losses the result is dropped and logged (the next probe replaces it). A check deleted meanwhile is skipped.
- Unique `(organisationId, name)` makes a duplicate name a domain 409 whatever the race, on create and on rename.
- **The claim** is one `findOneAndUpdate` per check, ordered by `nextDueAt`: it matches only ACTIVE checks that are due and moves `nextDueAt` forward (the lease, ADR-030) in the same operation, so two instances can never take the same check. It does not bump the version, so it never makes a staff edit lose.
- Indexes (created at startup, fail-open, idempotent, `thinklab.mongo.create-indexes=false` turns them off): the unique name, the claim `(status, nextDueAt)`, the list filters `(organisationId, health)` and `(organisationId, assetId)`, and for the results `(checkId, at desc)` plus the TTL.

## Consequences
- Positive: no lost update either way, no double probe, a result is never recorded against a stale copy.
- Negative: under heavy simultaneous edits a result can be dropped after three attempts, which is acceptable for a value that is replaced a few seconds later.
