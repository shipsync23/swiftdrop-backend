package com.swiftdrop.courier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Recipient name, note, photo path or failure reason recorded by the courier. */
@Entity
@Table(name = "delivery_proofs")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryProof {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "status_history_id", nullable = false)
    private UUID statusHistoryId;

    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Column(length = 500)
    private String note;

    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
