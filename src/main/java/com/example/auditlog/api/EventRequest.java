package com.example.auditlog.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;

public class EventRequest {
    @NotBlank public String eventType;
    @NotBlank public String actorId;
    @NotBlank public String resourceType;
    @NotBlank public String resourceId;
    @NotNull public Map<String, Object> payload;
    public OffsetDateTime timestamp;
}
