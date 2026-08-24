package com.example.auditlog;

import com.example.auditlog.api.EventRequest;
import com.example.auditlog.api.EventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuditServiceIntegrationTest {
    @Autowired private AuditService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuditController controller;

    @Test
    void appendAndVerifyWithOffsetTimestamp() {
        EventRequest request = new EventRequest();
        request.eventType = "USER_LOGIN";
        request.actorId = "user-1";
        request.resourceType = "account";
        request.resourceId = "acct-1";
        request.timestamp = OffsetDateTime.parse("2026-08-24T10:00:00+05:00");
        request.payload = new LinkedHashMap<>();
        request.payload.put("ip", "203.0.113.10");

        EventResponse event = service.append(request);

        assertThat(event.previousHash).isNotBlank();
        assertThat(service.verify().get("intact")).isEqualTo(true);
    }

    @Test
    void redactionPreservesAValidCurrentChain() {
        EventRequest request = new EventRequest();
        request.eventType = "RECORD_UPDATED";
        request.actorId = "user-2";
        request.resourceType = "account";
        request.resourceId = "acct-2";
        request.payload = new LinkedHashMap<>();
        request.payload.put("accountNumber", "123456789");
        EventResponse event = service.append(request);

        EventResponse redacted = service.redact(event.id, Collections.singleton("accountNumber"));

        assertThat(redacted.payload.get("accountNumber")).isEqualTo("[REDACTED]");
        assertThat(service.verify().get("intact")).isEqualTo(true);
    }

    @Test
    void composedFiltersAndPaginationReturnOnlyMatchingEvents() {
        append("ACCESS_GRANTED", "regulator-1", "acct-filter", "2026-08-24T11:00:00Z");
        append("ACCESS_DENIED", "regulator-1", "acct-filter", "2026-08-24T11:01:00Z");
        append("ACCESS_GRANTED", "service-1", "acct-filter", "2026-08-24T11:02:00Z");

        assertThat(service.query("regulator-1", "account", "acct-filter", "ACCESS_GRANTED",
            null, null, 0, 1)).hasSize(1)
            .first().extracting(event -> event.eventType).isEqualTo("ACCESS_GRANTED");
        assertThat(service.query("regulator-1", null, "acct-filter", null,
            null, null, 1, 1)).hasSize(1)
            .first().extracting(event -> event.eventType).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void archiveKeepsRecordsPresentAndVerifiable() {
        EventResponse event = append("ACCESS_GRANTED", "regulator-2", "acct-archive", "2026-08-23T10:00:00Z");

        assertThat(service.archiveBefore(OffsetDateTime.parse("2026-08-24T00:00:00Z"))).isGreaterThanOrEqualTo(1);
        assertThat(service.find(event.id).archived).isTrue();
        assertThat(service.verify().get("intact")).isEqualTo(true);
    }

    @Test
    void exportContainsChainMetadataAndSelectedRecords() {
        append("ACCESS_GRANTED", "regulator-3", "acct-export", "2026-08-24T12:00:00Z");

        Map<String, Object> bundle = controller.export("regulator-3", null);

        assertThat(bundle).containsEntry("format", "audit-chain-bundle-v1")
            .containsEntry("genesis", "GENESIS");
        assertThat(bundle.get("records")).asList().extracting("resourceId").contains("acct-export");
    }

    @Test
    void directContentTamperingIsReported() {
        EventResponse event = append("ACCESS_GRANTED", "regulator-4", "acct-tamper", "2026-08-24T13:00:00Z");
        jdbc.update("UPDATE audit_events SET content_hash=? WHERE id=?", "tampered", event.id);

        assertThat(service.verify()).containsEntry("intact", false)
            .containsEntry("firstInconsistentRecordId", event.id)
            .containsEntry("violation", "CONTENT_HASH_MISMATCH");
    }

    private EventResponse append(String eventType, String actorId, String resourceId, String timestamp) {
        EventRequest request = new EventRequest();
        request.eventType = eventType;
        request.actorId = actorId;
        request.resourceType = "account";
        request.resourceId = resourceId;
        request.timestamp = OffsetDateTime.parse(timestamp);
        request.payload = new LinkedHashMap<>();
        request.payload.put("result", eventType);
        return service.append(request);
    }
}
