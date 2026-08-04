package com.bridgelab.payment.temporal;

import com.bridgelab.payment.model.PaymentRequest;
import com.bridgelab.payment.model.PaymentResult;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Orchestrates: validate request -> fraud check -> risk decision -> publish
 * outcome (via Dapr pub/sub). Dapr's Pub/Sub building block is unchanged --
 * it is simply called from inside Temporal activities.
 *
 * The fraud check itself is asynchronous: this workflow publishes a
 * fraud-check-requests event carrying its own workflow ID, then blocks on
 * Workflow.await() until Fraud Service processes that event and calls back
 * with a Signal (receiveFraudResult). This is the async request/reply
 * pattern from the client's Temporal Flow deck, adapted to this project's
 * Dapr Pub/Sub transport instead of raw Kafka.
 */
@WorkflowInterface
public interface PaymentProcessingWorkflow {

    @WorkflowMethod
    PaymentResult process(PaymentRequest request);

    @SignalMethod
    void receiveFraudResult(double riskScore);
}
