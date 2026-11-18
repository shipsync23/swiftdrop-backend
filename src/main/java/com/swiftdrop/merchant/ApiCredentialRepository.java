package com.swiftdrop.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiCredentialRepository extends JpaRepository<ApiCredential, UUID> {

    Optional<ApiCredential> findByKeyHash(String keyHash);

    List<ApiCredential> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
