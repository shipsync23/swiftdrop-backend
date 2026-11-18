package com.swiftdrop.webhook;

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

/** One webhook attempt to a merchant: SUCCESS, RETRY (will try again) or DEAD (moved to webhook.dlq). */
@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;
}
