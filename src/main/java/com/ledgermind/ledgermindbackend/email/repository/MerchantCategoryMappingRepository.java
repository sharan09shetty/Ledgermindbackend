package com.ledgermind.ledgermindbackend.email.repository;

import com.ledgermind.ledgermindbackend.email.entity.MerchantCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantCategoryMappingRepository
        extends JpaRepository<MerchantCategoryMapping, MerchantCategoryMapping.Key> {

    Optional<MerchantCategoryMapping> findByUserIdAndMerchant(UUID userId, String merchant);
}
