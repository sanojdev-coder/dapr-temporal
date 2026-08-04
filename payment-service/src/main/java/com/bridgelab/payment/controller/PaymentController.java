package com.bridgelab.payment.controller;

import com.bridgelab.payment.model.PaymentRequest;
import com.bridgelab.payment.model.PaymentResult;
import com.bridgelab.payment.temporal.PaymentProcessingWorkflow;
import com.bridgelab.payment.temporal.TemporalWorkerConfig;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final WorkflowClient workflowClient;

    public PaymentController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping
    public ResponseEntity<PaymentResult> submitPayment(@RequestBody PaymentRequest request) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TemporalWorkerConfig.TASK_QUEUE)
                .setWorkflowId("payment-" + request.getTransactionId())
                // Circuit breaker: don't let a stuck workflow hold the HTTP
                // handler open indefinitely.
                .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                .build();

        PaymentProcessingWorkflow workflow =
                workflowClient.newWorkflowStub(PaymentProcessingWorkflow.class, options);

        // Synchronous start+wait keeps the request/response contract identical
        // to the original assignment -- caller doesn't need to know Temporal exists.
        PaymentResult result = workflow.process(request);

        return ResponseEntity.ok(result);
    }
}
