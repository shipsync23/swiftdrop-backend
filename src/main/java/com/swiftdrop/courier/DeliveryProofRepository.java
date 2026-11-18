package com.swiftdrop.courier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, UUID> {

    List<DeliveryProof> findByParcelIdOrderByCreatedAtDesc(UUID parcelId);
}
