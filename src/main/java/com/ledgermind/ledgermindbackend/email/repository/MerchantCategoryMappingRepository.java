package com.ledgermind.ledgermindbackend.email.repository;

import com.ledgermind.ledgermindbackend.email.entity.MerchantCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantCategoryMappingRepository
        extends JpaRepository<MerchantCategoryMapping, String> {
}