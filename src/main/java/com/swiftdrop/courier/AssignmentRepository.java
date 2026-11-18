package com.swiftdrop.courier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByParcelIdAndActiveTrue(UUID parcelId);

    boolean existsByParcelIdAndCourierIdAndActiveTrue(UUID parcelId, UUID courierId);

    /** Number of unfinished deliveries per courier (report: "monitor courier workloads"). */
    @Query("""
            select a.courier.id, count(a) from Assignment a, Parcel p
            where p.id = a.parcelId and a.active = true
              and p.status not in (com.swiftdrop.parcel.ParcelStatus.DELIVERED, com.swiftdrop.parcel.ParcelStatus.FAILED)
            group by a.courier.id
            """)
    List<Object[]> countActiveByCourier();

    @Query("select a.parcelId from Assignment a where a.active = true and a.courier.id = :courierId")
    List<UUID> activeParcelIdsForCourier(@Param("courierId") UUID courierId);
}
