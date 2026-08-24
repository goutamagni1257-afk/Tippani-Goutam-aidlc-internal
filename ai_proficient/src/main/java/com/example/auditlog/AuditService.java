package com.example.auditlog;

import com.example.auditlog.api.EventRequest;
import com.example.auditlog.api.EventResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AuditService {
    private static final String GENESIS = "GENESIS";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = JsonMapper.builder().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).build();

    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public EventResponse append(EventRequest request) {
        OffsetDateTime timestamp = request.timestamp == null ? OffsetDateTime.now() : request.timestamp;
        String payload = json(request.payload);
        String previous = jdbc.query("SELECT content_hash FROM audit_events ORDER BY id DESC LIMIT 1", rs -> rs.next() ? rs.getString(1) : GENESIS);
        String content = hash(request.eventType, request.actorId, request.resourceType, request.resourceId, payload, timestamp.toInstant().toString());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO audit_events(event_type,actor_id,resource_type,resource_id,payload_json,occurred_at,previous_hash,content_hash) VALUES(?,?,?,?,?,?,?,?)",
                new String[]{"ID"});
            statement.setString(1, request.eventType); statement.setString(2, request.actorId);
            statement.setString(3, request.resourceType); statement.setString(4, request.resourceId);
            statement.setString(5, payload); statement.setTimestamp(6, Timestamp.from(timestamp.toInstant()));
            statement.setString(7, previous); statement.setString(8, content);
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return find(id);
    }

    public List<EventResponse> query(String actorId, String resourceType, String resourceId, String eventType, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_events WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (actorId != null) { sql.append(" AND actor_id=?"); args.add(actorId); }
        if (resourceType != null) { sql.append(" AND resource_type=?"); args.add(resourceType); }
        if (resourceId != null) { sql.append(" AND resource_id=?"); args.add(resourceId); }
        if (eventType != null) { sql.append(" AND event_type=?"); args.add(eventType); }
        if (from != null) { sql.append(" AND occurred_at>=?"); args.add(Timestamp.from(from.toInstant())); }
        if (to != null) { sql.append(" AND occurred_at<=?"); args.add(Timestamp.from(to.toInstant())); }
        sql.append(" ORDER BY id LIMIT ? OFFSET ?"); args.add(size); args.add(page * size);
        return jdbc.query(sql.toString(), args.toArray(), (rs, n) -> map(rs));
    }

    public Map<String, Object> verify() {
        List<EventResponse> events = jdbc.query("SELECT * FROM audit_events ORDER BY id", (rs, n) -> map(rs));
        String previous = GENESIS;
        for (EventResponse event : events) {
            if (!previous.equals(event.previousHash)) return broken(event, "PREVIOUS_HASH_MISMATCH");
            if (!event.redacted && !event.contentHash.equals(hash(event.eventType, event.actorId, event.resourceType, event.resourceId, json(event.payload), event.timestamp.toInstant().toString()))) return broken(event, "CONTENT_HASH_MISMATCH");
            previous = event.contentHash;
        }
        return new LinkedHashMap<String, Object>() {{ put("intact", true); put("recordsChecked", events.size()); }};
    }

    @Transactional
    public EventResponse redact(long id, Set<String> fields) {
        EventResponse event = find(id);
        Map<String, Object> payload = new LinkedHashMap<>(event.payload);
        fields.forEach(field -> { if (payload.containsKey(field)) payload.put(field, "[REDACTED]"); });
        jdbc.update("UPDATE audit_events SET payload_json=?, redacted=TRUE WHERE id=?", json(payload), id);
        rebuildHashesFrom(id);
        return find(id);
    }

    @Transactional
    public int archiveBefore(OffsetDateTime cutoff) {
        return jdbc.update("UPDATE audit_events SET archived=TRUE WHERE occurred_at<?", Timestamp.from(cutoff.toInstant()));
    }

    private void rebuildHashesFrom(long id) {
        List<EventResponse> events = jdbc.query("SELECT * FROM audit_events WHERE id>=? ORDER BY id", new Object[]{id}, (rs, n) -> map(rs));
        String previous = jdbc.query("SELECT previous_hash FROM audit_events WHERE id=?", new Object[]{id}, rs -> rs.next() ? rs.getString(1) : GENESIS);
        for (EventResponse event : events) {
            String content = hash(event.eventType, event.actorId, event.resourceType, event.resourceId, json(event.payload), event.timestamp.toInstant().toString());
            jdbc.update("UPDATE audit_events SET previous_hash=?, content_hash=? WHERE id=?", previous, content, event.id);
            previous = content;
        }
    }

    public EventResponse find(long id) { return jdbc.queryForObject("SELECT * FROM audit_events WHERE id=?", new Object[]{id}, (rs, n) -> map(rs)); }
    private EventResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        EventResponse e = new EventResponse(); e.id=rs.getLong("id"); e.eventType=rs.getString("event_type"); e.actorId=rs.getString("actor_id"); e.resourceType=rs.getString("resource_type"); e.resourceId=rs.getString("resource_id"); e.payload=read(rs.getString("payload_json")); e.timestamp=rs.getTimestamp("occurred_at").toInstant().atOffset(java.time.ZoneOffset.UTC); e.previousHash=rs.getString("previous_hash"); e.contentHash=rs.getString("content_hash"); e.archived=rs.getBoolean("archived"); e.redacted=rs.getBoolean("redacted"); return e;
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalArgumentException("payload must be JSON serializable", e); } }
    @SuppressWarnings("unchecked") private Map<String,Object> read(String value) { try { return mapper.readValue(value, Map.class); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    private String hash(String... values) { try { MessageDigest digest=MessageDigest.getInstance("SHA-256"); String joined=String.join("|", values); byte[] bytes=digest.digest(joined.getBytes(StandardCharsets.UTF_8)); StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format("%02x", b)); return out.toString(); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Map<String,Object> broken(EventResponse event, String violation) { Map<String,Object> result=new LinkedHashMap<>(); result.put("intact", false); result.put("firstInconsistentRecordId", event.id); result.put("violation", violation); return result; }
}
