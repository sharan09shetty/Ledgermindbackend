package com.ledgermind.ledgermindbackend.queue.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgermind.ledgermindbackend.email.entity.RawEmail;
import com.ledgermind.ledgermindbackend.queue.dto.RawEmailMessage;
import com.ledgermind.ledgermindbackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RawEmailSnsPublisher {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.raw-email-topic-arn}")
    private String topicArn;

    public void publish(RawEmail email, User user) {
        try {
            String payload = objectMapper.writeValueAsString(new RawEmailMessage(
                    email.getId(),
                    email.getUserId(),
                    email.getSender(),
                    email.getBody(),
                    email.getReceivedAt(),
                    user.getTelegramChatId()
            ));

            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(payload)
                    .build());

            log.info("Published rawEmailId={} to SNS", email.getId());

        } catch (Exception e) {
            log.error("Failed to publish rawEmailId={} to SNS, will rely on fallback poll", email.getId(), e);
        }
    }
}