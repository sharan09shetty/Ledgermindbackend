package com.ledgermind.ledgermindbackend.queue.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgermind.ledgermindbackend.email.service.TransactionProcessingService;
import com.ledgermind.ledgermindbackend.queue.dto.RawEmailMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RawEmailSqsConsumer {

    private final TransactionProcessingService transactionProcessingService;
    private final ObjectMapper objectMapper;

    @SqsListener("${aws.sqs.raw-email-queue-url}")
    public void consume(String messageBody) {
        RawEmailMessage message;
        try {
            message = objectMapper.readValue(messageBody, RawEmailMessage.class);
        } catch (Exception e) {
            log.error("Failed to deserialize SQS message: {}", messageBody, e);
            throw new RuntimeException("Deserialization failed", e);
        }
        transactionProcessingService.processSingleEmail(message);
    }
}