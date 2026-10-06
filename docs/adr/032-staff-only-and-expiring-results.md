# ADR-032: Staff Only, and Probe Results Expire

## Status
Accepted

## Context
Monitoring data describes the internal network, and a probe every ten seconds per check is a lot of rows.

## Decision
- **Staff only:** every route refuses a `REQUESTER` with 403 `ERR-HLM-00403`; `X-Tenant-Id` is mandatory everywhere and another tenant's check is a 404.
- Every probe is stored as one small document in a **separate collection** (`checkId`, `organisationId`, time, ok, status code, latency, the fixed error code) and a **TTL index removes it after 7 days**, so the history is bounded without a cleanup job. The check itself keeps only its latest observation (last time, latency, error), its counters and its last change of health.
- `results/retrieve` answers the recent probes, newest first (default 50, at most 500), for a check of the tenant. A failure to write a result is logged and never stops the recording of the health.
- `summary/retrieve` counts the tenant's checks that are being probed by health, and the paused ones apart, for a one-glance view.
- No DELETE: a check is paused. The audit trail and the results are never edited.

## Consequences
- Positive: bounded storage, nothing to schedule for cleanup, a cheap summary.
- Negative: the summary reads the tenant's checks and counts them in memory, which is fine for thousands of checks and would need a counting query beyond that; history older than 7 days is gone (long-term trends need an export, not built).
