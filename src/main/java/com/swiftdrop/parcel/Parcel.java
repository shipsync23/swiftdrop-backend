package com.swiftdrop.parcel;

import com.swiftdrop.pricing.ParcelSize;
import com.swiftdrop.pricing.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A delivery request, created by a customer (web) or a merchant (API). */
@Entity
@Table(name = "parcels")
@Getter
@Setter
@NoArgsConstructor
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 20)
    private String trackingNumber;

    /** Set for customer bookings. */
    @Column(name = "customer_id")
    private UUID customerId;

    /** Set for merchant API orders. */
    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Column(name = "dropoff_address", nullable = false, length = 500)
    private String dropoffAddress;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "dest_zone_id", nullable = false)
    private Zone destZone;

    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "parcel_size", nullable = false, length = 10)
    private ParcelSize size;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ParcelStatus status;

    /** Optimistic locking: an outdated update fails instead of overwriting a newer one. */
    @Version
    @Column(nullable = false)
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
