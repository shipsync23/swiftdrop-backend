package com.swiftdrop.parcel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Append-only record of every status change (never updated or deleted). */
@Entity
@Table(name = "status_history")
@Getter
@Setter
@NoArgsConstructor
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 30)
    private ParcelStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private ParcelStatus newStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
