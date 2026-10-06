# micronaut-it-health-monitoring-service

BIAN-aligned Service Domain **it-health-monitoring** (Control Record: `HealthCheck`), port `8103`.

The first slice of Journey 14 (operations): the platform watches things on a schedule and says whether they answer. A `HealthCheck` is an
**HTTP** address or a **TCP** host and port, optionally tied to an asset by id, probed by this service every few seconds or minutes. It
keeps what the probes saw: up, down or not known yet, how long the last probe took, what went wrong, and since when.

## What it guarantees, and what it does not

- **A blip is not an outage** (ADR-030): a check goes `DOWN` after `failureThreshold` failures in a row (default 3) and `UP` after
  `successThreshold` successes in a row (default 1). Only a change of health and the staff actions enter the audit trail (its last 200
  entries), never every probe.
- **Safe with several instances** (ADR-030, ADR-033): the scheduler claims the due checks with one atomic find-and-update each, which also
  leases them, so two instances never probe the same check; every write is guarded by the version loaded, and a probe result is applied to
  a fresh copy with up to three retries.
- **A probe cannot be aimed where it should not go** (ADR-031): private networks are allowed (that is what a monitor watches); link-local
  addresses (the cloud metadata service), the unspecified address, multicast and the metadata names never are, and loopback only when the
  deployment allows it. The rules run on the literal when a check is written **and on what the name resolves to at every probe**. HTTP
  probes do not follow redirects, discard the body unread and send no credentials (a target may not carry userinfo, a query or a fragment).
- **Nothing a target says is kept** (ADR-031): a failure is one of `timeout`, `connection refused`, `dns failure`, `unexpected status`,
  `address not allowed`, `probe failed`, plus the status code and the latency.
- **Staff only** (ADR-032): every route refuses a `REQUESTER` with 403 `ERR-HLM-00403`. The probe history is a separate collection that
  expires after 7 days (a TTL index).
- **Not here yet:** alert rules and incidents opened from a check going down (the next slice), monitoring an endpoint that needs
  credentials, agents that push results from isolated networks, response-body or latency assertions, maintenance windows that mute a
  check, DNS rebinding defence for HTTP, events, and long-term trends.

## BIAN Behavior Qualifier Contract

`X-Tenant-Id` is mandatory on every call and scopes it (another tenant's check answers 404); `X-Executor` is mandatory on the actions;
`X-Role` is optional and, with platform security on, comes from the verified token.

| Behavior Qualifier | Route |
|---|---|
| initiate | `POST /it-health-monitoring/v1/initiate` `{"name":"API","type":"HTTP","target":"https://api.acme.test/health","assetId":"<uuid>","intervalSeconds":60,"timeoutMillis":5000,"expectedStatus":200,"failureThreshold":3,"successThreshold":1}` (everything after the target is optional) or `{"name":"DB","type":"TCP","target":"db.internal:5432"}` |
| retrieve | `GET /it-health-monitoring/v1/{id}/retrieve` |
| retrieve (collection) | `GET /it-health-monitoring/v1/retrieve?health=&status=&assetId=` |
| summary/retrieve | `GET /it-health-monitoring/v1/summary/retrieve` (total, up, down, unknown among the active ones, and paused) |
| update | `PUT /it-health-monitoring/v1/{id}/update` (the whole definition again; the type cannot change; a new target starts the health from UNKNOWN) |
| control/pause | `PUT /it-health-monitoring/v1/{id}/control/pause` (ACTIVE -> PAUSED) |
| control/resume | `PUT /it-health-monitoring/v1/{id}/control/resume` (PAUSED -> ACTIVE, probed at once) |
| check/execute | `PUT /it-health-monitoring/v1/{id}/check/execute` (probes right now, whatever the status, and answers the check after) |
| results/retrieve | `GET /it-health-monitoring/v1/{id}/results/retrieve?limit=` (newest first, default 50, at most 500, kept 7 days) |
| audit-log/retrieve | `GET /it-health-monitoring/v1/{id}/audit-log/retrieve` |

```text
status:  ACTIVE <-> PAUSED
health:  UNKNOWN -> UP <-> DOWN      (UP after N successes in a row, DOWN after M failures in a row)
```

An HTTP check is up when the status is the expected one, or 200 to 399 when none is set. A TCP check is up when the connection opens.
Interval 10 s to 24 h, timeout 100 ms to 30 s (never longer than the interval), thresholds 1 to 10.

```bash
curl -X PUT "http://localhost:8103/it-health-monitoring/v1/<id>/check/execute" -H "X-Tenant-Id: <organisationId>" -H "X-Executor: <userId>"
# {"name":"API","health":"UP","lastLatencyMillis":14,"consecutiveFailures":0,...}
```

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-HLM-00403` | 403 | A REQUESTER used health monitoring (ADR-032) |
| `ERR-HLM-00404` | 404 | Check not found (another tenant's answers the same) |
| `ERR-HLM-00409` | 409 | Duplicate name, illegal transition (pause a paused check), or the check changed while the write was applied (retry) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure (a forbidden or malformed target, an interval or timeout out of range...) |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## Configuration

`thinklab.health-monitoring.allow-loopback` (env `THINKLAB_HEALTH_ALLOW_LOOPBACK`, default false), `scheduler-enabled` (env
`THINKLAB_HEALTH_SCHEDULER_ENABLED`, default true: turn it off for an instance that only serves the API), `claim-batch` (20),
`concurrency` (10) and `tick` (5s).

Do not put personal data in a check name or target: they are stored with the check.

## Architecture decisions

001 hexagonal architecture · 005 UUID identity sovereignty and audit tracing · 013 BIAN conventions · 019 HTTP 409 for state conflicts ·
030 health from thresholds, scheduled probes with a lease · 031 probe safety · 032 staff only, expiring results · 033 version guard and
atomic claim.

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
