package com.ledgermind.ledgermindbackend.email.service;

import com.ledgermind.ledgermindbackend.ai.service.AICategorizationService;
import com.ledgermind.ledgermindbackend.email.entity.MerchantCategoryMapping;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import com.ledgermind.ledgermindbackend.email.repository.MerchantCategoryMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategorizationService {

    private final AICategorizationService aiCategorizationService;
    private final MerchantCategoryMappingRepository categoryMappingRepository;

    public Category categorize(Transaction transaction) {
        return categoryMappingRepository.findById(transaction.getCounterparty())
                .map(MerchantCategoryMapping::getCategory)
                .orElseGet(() -> categorizeUsingAI(transaction));
    }

    public Category categorizeUsingAI(Transaction transaction) {
        Category category = aiCategorizationService.categorize(transaction);
        MerchantCategoryMapping mapping = MerchantCategoryMapping.builder()
                .merchant(transaction.getCounterparty())
                .category(category)
                .build();
        categoryMappingRepository.save(mapping);
        return category;
    }
}
