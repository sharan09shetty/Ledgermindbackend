package com.ledgermind.ledgermindbackend.email.service;

import com.ledgermind.ledgermindbackend.email.entity.RawEmail;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;

public interface TransactionParser {
    boolean supports(RawEmail email);

    /**
     * Parses the email into a Transaction, or returns {@code null} when the
     * email is from the bank but isn't an actual money movement (e.g. an
     * upcoming e-mandate/auto-pay reminder or an OTP/promo alert).
     */
    Transaction parse(RawEmail email);
}
