# Scenario Evidence

## A: Core Service

Decomposition: define the event contract, create the append-only schema, implement canonical SHA-256 hashing, add query filters and pagination, expose verification, then test direct-store tampering. Acceptance: events append, no update/delete API exists, filters compose, and verification reports the first mismatch.

## B: Retention, Redaction, Export

Archived records remain physically present and participate in verification. Redaction is explicit and auditable through the changed redacted state; hashes are rebuilt forward because successor links depend on them. Export includes every selected record's hashes, the genesis value, filter, and boundary metadata. The prototype's limitation is that exports are not digitally signed; a production bundle should be signed with a managed key.

## C: Clarified Compliance Requirement

Clarified requirement: "An authorized regulator can retrieve a time-bounded, read-only, tamper-evident history of successful and failed access attempts to a client account, filtered by account, actor, event type, and time range, with enough metadata to independently verify the returned records."

Questions/assumptions: define regulator identity and authorization, required retention period, timezone, pagination limits, whether failed attempts and service-to-service actors count, and whether export signatures are required. This prototype assumes an already-authorized caller, UTC comparisons, and client account identity represented by `resourceType` plus `resourceId`.

Implemented: event capture, read-only query, verification, and filtered export. Scoped out: authentication/authorization, immutable remote anchoring, key management, and regulator-specific report formatting because those require deployment and compliance decisions outside this prototype.
