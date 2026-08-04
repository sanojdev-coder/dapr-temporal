package com.bridgelab.payment.temporal;

import com.bridgelab.payment.model.PaymentRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Same interface backs both the local activity stub (validateRequest,
 * cheap, in-process, no Dapr call) and the remote activity stubs
 * (requestFraudCheck, publishEvent -- both delegate to Dapr Pub/Sub and
 * therefore get Temporal's retry/timeout policy).
 *
 * requestFraudCheck is fire-and-forget: it publishes the request and
 * returns immediately. The actual risk score arrives later via the
 * workflow's receiveFraudResult signal, not as this activity's return value.
 */
@ActivityInterface
public interface PaymentActivities {

    @ActivityMethod
    void validateRequest(PaymentRequest request);

    @ActivityMethod
    void requestFraudCheck(PaymentRequest request, String workflowId);

    @ActivityMethod
    void publishEvent(PaymentRequest request, String status, double riskScore, String reason);
}
