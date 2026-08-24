# AI Usage Log

This log records the assisted engineering process and must be kept honest as work continues.

| Date | Task | Outcome | Engineer decision |
|---|---|---|---|
| 2026-08-24 | Translate assignment into project structure | Accepted with edits | Chose Java/Spring Boot to match the engineer's backend experience and local runtime constraints. |
| 2026-08-24 | Draft hash-chain service and API | Accepted after review | Rejected opaque persistence magic; kept explicit JDBC and canonical JSON hashing. |
| 2026-08-24 | Review append identity handling | Modified | Replaced an invalid insert/query pattern with `GeneratedKeyHolder` after reasoning about JDBC behavior. |
| 2026-08-24 | Draft Scenario B design | Accepted with limitation | Redaction rebuilds the chain; documented that an external anchor is needed to detect the redaction itself. |

Engineer sign-off: **REQUIRED BEFORE SUBMISSION**
