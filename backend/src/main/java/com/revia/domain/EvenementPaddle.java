package com.revia.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Événement de webhook Paddle déjà traité (idempotence : un event_id n'est appliqué qu'une fois). */
@Entity
@Table(name = "paddle_evenement")
public class EvenementPaddle {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(nullable = false, length = 80)
    private String type;

    @Column(name = "recu_le", nullable = false)
    private LocalDateTime recuLe = LocalDateTime.now();

    protected EvenementPaddle() {
    }

    public EvenementPaddle(String eventId, String type) {
        this.eventId = eventId;
        this.type = type;
    }

    public String getEventId() { return eventId; }
    public String getType() { return type; }
    public LocalDateTime getRecuLe() { return recuLe; }
}
