package com.bridgelab.payment.temporal;

import com.bridgelab.payment.model.PaymentRequest;
import com.bridgelab.payment.model.PaymentResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.time.Instant;

public class PaymentProcessingWorkflowImpl implements PaymentProcessingWorkflow {

    // Local activity: in-process, zero network hop, short duration.
    private final PaymentActivities localActivities = Workflow.newLocalActivityStub(
            PaymentActivities.class,
            LocalActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(2))
                    .build()
    );

    // Remote activity: publishes the fraud-check request via Dapr Pub/Sub.
    // This call itself is just "did the publish succeed" -- short timeout,
    // a couple of retries covers a transient Redis blip. The actual risk
    // score comes back later via receiveFraudResult(), not from this call.
    private final PaymentActivities requestFraudCheckActivity = Workflow.newActivityStub(
            PaymentActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(3))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2)
                            .setMaximumAttempts(3)
                            .build())
                    .build()
    );

    // Remote activity: publishes to payment-events via Dapr Pub/Sub (Redis).
    private final PaymentActivities publishActivity = Workflow.newActivityStub(
            PaymentActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(3))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2)
                            .setMaximumAttempts(3)
                            .build())
                    .build()
    );

    private static final double HIGH_RISK_THRESHOLD = 0.8;
    // How long the workflow will wait for Fraud Service to signal back
    // before treating the fraud check as failed.
    private static final Duration FRAUD_RESULT_WAIT_TIMEOUT = Duration.ofSeconds(10);

    // Set by receiveFraudResult() when Fraud Service signals back. Workflow
    // state, so it must only be mutated from workflow/signal code, never
    // from an activity.
    private Double fraudRiskScore = null;

    @Override
    public void receiveFraudResult(double riskScore) {
        this.fraudRiskScore = riskScore;
    }

    @Override
    public PaymentResult process(PaymentRequest request) {
        localActivities.validateRequest(request);

        String workflowId = Workflow.getInfo().getWorkflowId();
        requestFraudCheckActivity.requestFraudCheck(request, workflowId);

        // Blocks the workflow (not a thread -- Temporal parks this durably)
        // until either receiveFraudResult() sets fraudRiskScore, or the
        // timeout elapses. This is the async request/reply correlation
        // pattern from the client's Temporal Flow deck: one service starts
        // the workflow, a different service (Fraud Service) delivers the
        // result later via Signal instead of a blocking synchronous call.
        boolean signaled = Workflow.await(FRAUD_RESULT_WAIT_TIMEOUT, () -> fraudRiskScore != null);

        if (!signaled) {
            // Fraud Service never signaled back in time: fail safe
            // (deny-by-default) and still publish, so the audit trail
            // captures the timeout as a legitimate outcome rather than
            // losing it silently.
            publishActivity.publishEvent(request, "BLOCKED", -1, "FRAUD_SERVICE_TIMEOUT");
            return new PaymentResult(
                    request.getTransactionId(),
                    "BLOCKED",
                    -1,
                    "FRAUD_SERVICE_TIMEOUT",
                    nowAsIsoString()
            );
        }

        double riskScore = fraudRiskScore;
        boolean highRisk = riskScore > HIGH_RISK_THRESHOLD;
        String status = highRisk ? "BLOCKED" : "APPROVED";
        String reason = highRisk ? "HIGH_RISK" : null;

        publishActivity.publishEvent(request, status, riskScore, reason);

        return new PaymentResult(request.getTransactionId(), status, riskScore, reason, nowAsIsoString());
    }

    /** Workflow.currentTimeMillis() is deterministic/replay-safe; Instant.now() is not. */
    private String nowAsIsoString() {
        return Instant.ofEpochMilli(Workflow.currentTimeMillis()).toString();
    }
}
