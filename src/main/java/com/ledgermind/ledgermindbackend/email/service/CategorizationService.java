package com.ledgermind.ledgermindbackend.email.service;

import com.ledgermind.ledgermindbackend.email.entity.MerchantCategoryMapping;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import com.ledgermind.ledgermindbackend.email.repository.MerchantCategoryMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategorizationService {

    private final MerchantCategoryMappingRepository categoryMappingRepository;

    public Category categorize(Transaction transaction) {

        String merchant = transaction.getCounterparty();

        if (merchant == null || merchant.isBlank()) {
            return Category.OTHER;
        }

        return categoryMappingRepository.findById(
                        merchant.toUpperCase()
                )
                .map(MerchantCategoryMapping::getCategory)
                .orElse(Category.OTHER);
    }
}
