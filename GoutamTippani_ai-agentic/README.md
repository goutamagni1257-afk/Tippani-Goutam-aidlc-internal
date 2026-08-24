# Tamper-Evident Audit Log Service

## Track

`GoutamTippani_ai-agentic` project track. The implementation is based on the audit-log service baseline; track-specific requirements should be added here when provided.

Java/Spring Boot prototype for the audit-log assignment. The service stores append-only events in an embedded H2 database, calculates a SHA-256 hash chain, supports querying and verification, and includes the retention, redaction, export, and compliance-reporting slices required by the assessment.

## Requirements

- Java 8+
- Maven 3.9+ (or the Maven wrapper once generated)

## Run

```powershell
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

## Test

```powershell
mvn test
```

Build and test commands require Maven 3.9+ and a JDK. The current implementation is Java 8 compatible; install a JDK and Maven or add the Maven Wrapper before running the quality gates.

The complete design, scenarios, limitations, AI traceability, and final summary are in `docs/`.
