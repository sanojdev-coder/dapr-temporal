package com.bridgelab.payment.temporal;

import com.bridgelab.payment.model.PaymentRequest;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentActivitiesImpl implements PaymentActivities {

    private static final String PUBSUB_NAME = "payment-pubsub";
    private static final String FRAUD_REQUEST_TOPIC = "fraud-check-requests";
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private final DaprClient daprClient = new DaprClientBuilder().build();

    @Override
    public void validateRequest(PaymentRequest request) {
        if (request.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getCurrency() == null || request.getCurrency().isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
        if (request.getFromAccount() == null || request.getToAccount() == null) {
            throw new IllegalArgumentException("from_account and to_account are required");
        }
    }

    /**
     * Fire-and-forget: publishes the fraud check request via Dapr Pub/Sub,
     * tagged with this workflow's ID. Fraud Service consumes this event,
     * computes the risk score, then signals the workflow back directly --
     * it does not reply on this same topic/call.
     */
    @Override
    public void requestFraudCheck(PaymentRequest request, String workflowId) {
        Map<String, Object> event = new HashMap<>();
        event.put("transaction_id", request.getTransactionId());
        event.put("amount", request.getAmount());
        event.put("workflow_id", workflowId);

        daprClient.publishEvent(PUBSUB_NAME, FRAUD_REQUEST_TOPIC, event).block();
    }

    @Override
    public void publishEvent(PaymentRequest request, String status, double riskScore, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("transaction_id", request.getTransactionId());
        event.put("status", status);
        event.put("risk_score", riskScore);
        event.put("amount", request.getAmount());
        if (reason != null) {
            event.put("reason", reason);
        }

        daprClient.publishEvent(PUBSUB_NAME, PAYMENT_EVENTS_TOPIC, event).block();
    }
}
