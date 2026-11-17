package com.swiftdrop.parcel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    List<Parcel> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
