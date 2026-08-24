package com.example.auditlog.api;

import java.time.OffsetDateTime;
import java.util.Map;

public class EventResponse {
    public Long id;
    public String eventType;
    public String actorId;
    public String resourceType;
    public String resourceId;
    public Map<String, Object> payload;
    public OffsetDateTime timestamp;
    public String previousHash;
    public String contentHash;
    public boolean archived;
    public boolean redacted;
}
