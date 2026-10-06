# ADR-031: A Probe Must Not Be Turned Into a Way to Reach Where It Should Not

## Status
Accepted

## Context
The service makes network calls to addresses staff give it, from inside the platform's network. A monitor exists to watch internal infrastructure, so private networks cannot be blocked the way an outbound integration blocks them.

## Decision
- **Private networks are allowed. What is never allowed:** link-local addresses (the cloud metadata service lives at `169.254.169.254`, and `fd00:ec2::254` on IPv6), the unspecified address, multicast, and the well-known metadata names. **Loopback** is refused unless the deployment allows it (`thinklab.health-monitoring.allow-loopback`, for a test double or a local stack).
- The rules run twice. When a check is written, on the **literal** (dotted IPv4, bracketed IPv6, a plain decimal integer, `localhost`). And **at every probe, on what the name resolves to**: a name that now points at a forbidden address is refused, and if **any** of its addresses is forbidden the name is refused. A TCP probe then connects to exactly the address that was vetted; an HTTP probe connects by name again, so a name that changes between the check and the connection is a known, documented limit.
- **HTTP probes** send one `GET` with the check's timeout, **do not follow redirects** (a redirect is an answer, and following one could lead anywhere), **discard the body unread** and send no header from the check. There are no credentials to send: a target may not carry userinfo, a query or a fragment, which are where secrets end up and would be stored. A monitored endpoint that needs authentication is not supported yet.
- **What a probe reports is a fixed vocabulary**, never a message from the target or the network stack (those can carry addresses, tokens or banners): `timeout`, `connection refused`, `dns failure`, `unexpected status`, `address not allowed`, `probe failed`, plus the status code and the latency.
- The probe never fails as a stream: whatever goes wrong is a down result, so one bad target cannot break a round.

## Consequences
- Positive: a monitor that cannot be aimed at the metadata service, leaks nothing a target says, and cannot be dragged along by a redirect.
- Negative: DNS rebinding between the vetting and an HTTP connection is not closed; monitoring an authenticated endpoint needs a later decision about where its secret lives (by reference, as the ticketing connector does).
