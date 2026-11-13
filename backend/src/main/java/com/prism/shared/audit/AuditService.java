package com.prism.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(UUID actorId, String action, String entityType, UUID entityId, Map<String, Object> metadata, String ip) {
        AuditLog log = new AuditLog();
        log.actorId = actorId;
        log.action = action;
        log.entityType = entityType;
        log.entityId = entityId;
        log.ipAddress = ip;
        if (metadata != null && !metadata.isEmpty()) {
            log.metadata = objectMapper.valueToTree(metadata);
        }
        repository.save(log);
    }

    public void record(UUID actorId, String action, String entityType, UUID entityId, JsonNode metadata, String ip) {
        AuditLog log = new AuditLog();
        log.actorId = actorId;
        log.action = action;
        log.entityType = entityType;
        log.entityId = entityId;
        log.metadata = metadata;
        log.ipAddress = ip;
        repository.save(log);
    }
}
