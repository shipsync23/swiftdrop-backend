package com.swiftdrop.parcel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    List<Parcel> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Parcel> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    /** Parcels waiting for a courier: CREATED and no active assignment. */
    @Query("""
            select p from Parcel p
            where p.status = com.swiftdrop.parcel.ParcelStatus.CREATED
              and not exists (select a.id from Assignment a where a.parcelId = p.id and a.active = true)
            order by p.createdAt asc
            """)
    List<Parcel> findUnassigned();

    List<Parcel> findAllByOrderByCreatedAtDesc();

    List<Parcel> findByStatusOrderByCreatedAtDesc(ParcelStatus status);
}
