package com.revia.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Livraison de webhook GitHub déjà reçue (idempotence : un X-GitHub-Delivery n'est traité qu'une fois). */
@Entity
@Table(name = "github_livraison")
public class LivraisonGithub {

    @Id
    @Column(name = "delivery_id", length = 64)
    private String deliveryId;

    @Column(nullable = false, length = 40)
    private String evenement;

    @Column(name = "recu_le", nullable = false)
    private LocalDateTime recuLe = LocalDateTime.now();

    protected LivraisonGithub() {
    }

    public LivraisonGithub(String deliveryId, String evenement) {
        this.deliveryId = deliveryId;
        this.evenement = evenement;
    }

    public String getDeliveryId() { return deliveryId; }
    public String getEvenement() { return evenement; }
    public LocalDateTime getRecuLe() { return recuLe; }
}
