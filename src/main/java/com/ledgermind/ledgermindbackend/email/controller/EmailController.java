package com.ledgermind.ledgermindbackend.email.controller;

import com.google.api.services.gmail.model.Message;
import com.ledgermind.ledgermindbackend.email.service.GmailService;

import com.ledgermind.ledgermindbackend.email.service.TransactionProcessingService;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EmailController {

    private final GmailService gmailService;
    private final TransactionProcessingService transactionExtractionService;

    @PostMapping("/emails/test")
    public void testEmails() throws Exception {
        gmailService.fetchAndSaveEmails();
    }

    @GetMapping("/emails/test/{id}")
    public String getEmail(@PathVariable String id) throws Exception {
        return gmailService.fetchEmailById(id);
    }

    @GetMapping("/emails/{id}")
    public Message getEntireEmail(@PathVariable String id) throws Exception {
        return gmailService.fetchEntireEmailById(id);
    }

    @PostMapping("/emails/test/transaction")
    public void processTransaction(){
        transactionExtractionService.extractAndProcessTransactions();
    }

    @PostMapping("/test")
    public void processEndToEnd() throws Exception{
        gmailService.fetchAndSaveEmails();
        transactionExtractionService.extractAndProcessTransactions();
    }
}
