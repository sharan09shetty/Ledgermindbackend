package com.ledgermind.ledgermindbackend.ai.service;

import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;

public interface AICategorizationService {
    Category categorize(Transaction transaction);
}
