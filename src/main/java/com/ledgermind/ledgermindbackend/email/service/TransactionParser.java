package com.ledgermind.ledgermindbackend.email.service;

import com.ledgermind.ledgermindbackend.email.entity.RawEmail;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;

public interface TransactionParser {
    boolean supports(RawEmail email);
    Transaction parse(RawEmail email);
}
