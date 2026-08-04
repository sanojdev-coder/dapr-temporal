package com.bridgelab.payment.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * NOTE: the original assignment uses "processed_at" for approvals and
 * "blocked_at" for blocks. This POC unifies both into a single "timestamp"
 * field for simplicity -- trivial to split back into two field names if
 * the downstream contract needs to match the original spec exactly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResult implements Serializable {

    @JsonProperty("transaction_id")
    private String transactionId;

    private String status;

    @JsonProperty("risk_score")
    private double riskScore;

    private String reason;

    private String timestamp;

    public PaymentResult() {
    }

    public PaymentResult(String transactionId, String status, double riskScore, String reason, String timestamp) {
        this.transactionId = transactionId;
        this.status = status;
        this.riskScore = riskScore;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
