package com.ledgermind.ledgermindbackend.queue.consumer;

import com.ledgermind.ledgermindbackend.email.service.TransactionProcessingService;
import com.ledgermind.ledgermindbackend.queue.dto.RawEmailMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RawEmailSqsConsumer {

    private final TransactionProcessingService transactionProcessingService;

    @SqsListener(value = "${aws.sqs.raw-email-queue-url}", acknowledgementMode = "MANUAL")
    public void consume(RawEmailMessage message, Acknowledgement acknowledgement) {
        try {
            transactionProcessingService.processSingleEmail(message);
            acknowledgement.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process SQS message rawEmailId={}, leaving unacknowledged for retry",
                    message.rawEmailId(), e);
        }
    }
}