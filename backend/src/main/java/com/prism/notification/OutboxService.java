package com.prism.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String aggregateType, UUID aggregateId, String eventType, Map<String, ?> payload, String dedupeKey) {
        if (dedupeKey != null && repository.existsByDedupeKey(dedupeKey)) {
            return;
        }
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = objectMapper.valueToTree(payload == null ? Map.of() : payload);
        event.dedupeKey = dedupeKey;
        event.status = "PENDING";
        repository.save(event);
    }
}
