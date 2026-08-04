package com.bridgelab.fraud.temporal;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;

/**
 * Structurally matches PaymentProcessingWorkflow's signal method in
 * payment-service. Temporal only needs the signal name (derived from the
 * method name here: "receiveFraudResult") to match what's registered on the
 * running workflow -- the two services don't need to share a JAR or the
 * full workflow interface, which keeps them independently deployable.
 */
@WorkflowInterface
public interface PaymentWorkflowSignalProxy {

    @SignalMethod
    void receiveFraudResult(double riskScore);
}
