package com.prism.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "event_views")
public class EventView {

    @EmbeddedId
    public Key id;

    @Column(nullable = false)
    public int viewCount;

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "event_id")
        public UUID eventId;
        @Column(name = "viewed_on")
        public LocalDate viewedOn;

        public Key() {
        }

        public Key(UUID eventId, LocalDate viewedOn) {
            this.eventId = eventId;
            this.viewedOn = viewedOn;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key key)) {
                return false;
            }
            return eventId.equals(key.eventId) && viewedOn.equals(key.viewedOn);
        }

        @Override
        public int hashCode() {
            return eventId.hashCode() * 31 + viewedOn.hashCode();
        }
    }
}
