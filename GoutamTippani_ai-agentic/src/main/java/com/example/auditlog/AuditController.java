package com.example.auditlog;

import com.example.auditlog.api.EventRequest;
import com.example.auditlog.api.EventResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/audit")
public class AuditController {
    private final AuditService service;
    public AuditController(AuditService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse append(@Valid @RequestBody EventRequest request) { return service.append(request); }

    @GetMapping
    public List<EventResponse> query(@RequestParam(required=false) String actorId, @RequestParam(required=false) String resourceType, @RequestParam(required=false) String resourceId, @RequestParam(required=false) String eventType, @RequestParam(required=false) OffsetDateTime from, @RequestParam(required=false) OffsetDateTime to, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        if (page < 0 || size < 1 || size > 500) throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 500");
        return service.query(actorId, resourceType, resourceId, eventType, from, to, page, size);
    }

    @GetMapping("/verify")
    public Map<String,Object> verify() { return service.verify(); }

    @PostMapping("/{id}/redact")
    public EventResponse redact(@PathVariable long id, @RequestBody Set<String> fields) { return service.redact(id, fields); }

    @PostMapping("/retention/archive")
    public Map<String, Object> archive(@RequestParam OffsetDateTime before) {
        return Collections.<String, Object>singletonMap("archived", service.archiveBefore(before));
    }

    @GetMapping("/export")
    public Map<String, Object> export(@RequestParam(required=false) String actorId, @RequestParam(required=false) String resourceId) {
        List<EventResponse> records = service.query(actorId, null, resourceId, null, null, null, 0, 500);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("format", "audit-chain-bundle-v1");
        bundle.put("genesis", "GENESIS");
        bundle.put("filter", actorId != null ? Collections.singletonMap("actorId", actorId) : Collections.singletonMap("resourceId", resourceId));
        bundle.put("records", records);
        bundle.put("verificationNote", "Verify each contentHash from the canonical event fields and check previousHash links; the first record's previousHash is the external chain boundary.");
        return bundle;
    }
}
