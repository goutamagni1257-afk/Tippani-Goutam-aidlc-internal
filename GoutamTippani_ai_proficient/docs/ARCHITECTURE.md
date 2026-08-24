# Architecture

## Decisions

- Spring Boot 2.7 with Java 8 compatibility because the development machine provides Java 8. The code avoids framework-specific persistence abstractions so the storage boundary remains explicit.
- H2 file storage provides a runnable local prototype. Production deployment should use PostgreSQL or an equivalent durable store, with transactional serialization around appends.
- Server time is the default timestamp; callers may supply an event timestamp for imported events. Hashing uses SHA-256 over a canonical, sorted-key JSON payload and fixed field order.
- Each row stores `previous_hash` and `content_hash`. There are no update or delete APIs for ordinary records.
- Redaction replaces selected payload values, marks the row redacted, and rebuilds hashes from that row forward. This preserves a valid current chain but means the external chain anchor must be protected if historical redaction must itself be detectable.
- Archive is a visibility/lifecycle flag. Archived rows remain in the chain and verification, preventing false breaks.

## API

- `POST /audit` append an event.
- `GET /audit` query with `actorId`, `resourceType`, `resourceId`, `eventType`, `from`, `to`, `page`, and `size`.
- `GET /audit/verify` verify the complete chain and identify the first violation.
- `POST /audit/{id}/redact` body: JSON array of payload field names.
- `POST /audit/retention/archive?before=<ISO-8601>` archive old records.
- `GET /audit/export?actorId=...` or `?resourceId=...` return a verifiable bundle with chain metadata.
