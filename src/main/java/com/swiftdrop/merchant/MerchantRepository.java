package com.swiftdrop.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByUserId(UUID userId);

    List<Merchant> findAllByOrderByCreatedAtDesc();

    List<Merchant> findByApprovalStatusOrderByCreatedAtAsc(ApprovalStatus status);
}
