#!/bin/bash
set -e

ENDPOINT="http://localhost:4566"
REGION="ap-south-1"
AWS="aws --endpoint-url=$ENDPOINT --region=$REGION"

echo "==> Creating DLQ..."
DLQ_URL=$($AWS sqs create-queue --queue-name ledgermind-transaction-processing-dlq \
  --query 'QueueUrl' --output text)
DLQ_ARN=$($AWS sqs get-queue-attributes --queue-url "$DLQ_URL" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
echo "    DLQ ARN: $DLQ_ARN"

echo "==> Creating main SQS queue with redrive policy (max 3 attempts)..."
QUEUE_URL=$($AWS sqs create-queue \
  --queue-name ledgermind-transaction-processing \
  --attributes "{
    \"VisibilityTimeout\": \"90\",
    \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"
  }" \
  --query 'QueueUrl' --output text)
QUEUE_ARN=$($AWS sqs get-queue-attributes --queue-url "$QUEUE_URL" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
echo "    Queue URL: $QUEUE_URL"
echo "    Queue ARN: $QUEUE_ARN"

echo "==> Creating SNS topic..."
TOPIC_ARN=$($AWS sns create-topic --name ledgermind-raw-emails \
  --query 'TopicArn' --output text)
echo "    Topic ARN: $TOPIC_ARN"

echo "==> Subscribing SQS to SNS with RawMessageDelivery=true..."
SUB_ARN=$($AWS sns subscribe \
  --topic-arn "$TOPIC_ARN" \
  --protocol sqs \
  --notification-endpoint "$QUEUE_ARN" \
  --query 'SubscriptionArn' --output text)

$AWS sns set-subscription-attributes \
  --subscription-arn "$SUB_ARN" \
  --attribute-name RawMessageDelivery \
  --attribute-value true
echo "    Subscription ARN: $SUB_ARN (RawMessageDelivery=true)"

echo "==> Granting SNS permission to send to SQS..."
$AWS sqs set-queue-attributes \
  --queue-url "$QUEUE_URL" \
  --attributes "{
    \"Policy\": \"{\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Principal\\\":{\\\"Service\\\":\\\"sns.amazonaws.com\\\"},\\\"Action\\\":\\\"sqs:SendMessage\\\",\\\"Resource\\\":\\\"$QUEUE_ARN\\\",\\\"Condition\\\":{\\\"ArnEquals\\\":{\\\"aws:SourceArn\\\":\\\"$TOPIC_ARN\\\"}}}]}\"
  }"

echo ""
echo "==> Infrastructure ready. Add these to your application-local.properties:"
echo "    aws.sns.raw-email-topic-arn=$TOPIC_ARN"
echo "    aws.sqs.raw-email-queue-url=$QUEUE_URL"