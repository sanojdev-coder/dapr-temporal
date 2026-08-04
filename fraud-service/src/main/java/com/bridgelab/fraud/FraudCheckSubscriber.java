package com.bridgelab.fraud;

import com.bridgelab.fraud.temporal.PaymentWorkflowSignalProxy;
import io.temporal.client.WorkflowClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is the fraud check path actually used by the payment flow now.
 * Payment Service publishes a fraud-check-requests event (fire-and-forget)
 * carrying its workflow ID; this subscriber consumes it, applies the same
 * fixed scoring rule as the legacy /fraud/check endpoint, and signals the
 * result straight back to that workflow via the Temporal WorkflowClient --
 * no HTTP response to Payment Service at all.
 */
@RestController
public class FraudCheckSubscriber {

    private static final double AMOUNT_THRESHOLD = 10000.0;
    private static final double HIGH_RISK_SCORE = 0.95;
    private static final double LOW_RISK_SCORE = 0.15;

    private final WorkflowClient workflowClient;

    public FraudCheckSubscriber(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @GetMapping("/dapr/subscribe")
    public List<Map<String, String>> subscribe() {
        Map<String, String> subscription = new HashMap<>();
        subscription.put("pubsubname", "payment-pubsub");
        subscription.put("topic", "fraud-check-requests");
        subscription.put("route", "/fraud-check-requests");
        return List.of(subscription);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/fraud-check-requests")
    public ResponseEntity<Void> handleFraudCheckRequest(@RequestBody Map<String, Object> cloudEvent) {
        Map<String, Object> data = (Map<String, Object>) cloudEvent.get("data");
        double amount = ((Number) data.get("amount")).doubleValue();
        String workflowId = (String) data.get("workflow_id");

        double riskScore = amount > AMOUNT_THRESHOLD ? HIGH_RISK_SCORE : LOW_RISK_SCORE;

        try {
            PaymentWorkflowSignalProxy workflow =
                    workflowClient.newWorkflowStub(PaymentWorkflowSignalProxy.class, workflowId);
            workflow.receiveFraudResult(riskScore);
        } catch (Exception e) {
            // The workflow may already have timed out or completed (e.g. a
            // redelivered/late event). Don't fail this handler for that --
            // failing here would just cause Dapr to keep redelivering a
            // request nobody is waiting on anymore.
            System.err.println("[fraud-service] could not signal workflow " + workflowId + ": " + e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
