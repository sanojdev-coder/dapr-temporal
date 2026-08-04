package com.bridgelab.payment.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Mirrors the POST /v1/payments request body from the Payment Assignment:
 * transaction_id, amount, currency, from_account, to_account.
 */
public class PaymentRequest implements Serializable {

    @JsonProperty("transaction_id")
    private String transactionId;

    private double amount;

    private String currency;

    @JsonProperty("from_account")
    private String fromAccount;

    @JsonProperty("to_account")
    private String toAccount;

    public PaymentRequest() {
    }

    public PaymentRequest(String transactionId, double amount, String currency, String fromAccount, String toAccount) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.currency = currency;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getFromAccount() {
        return fromAccount;
    }

    public void setFromAccount(String fromAccount) {
        this.fromAccount = fromAccount;
    }

    public String getToAccount() {
        return toAccount;
    }

    public void setToAccount(String toAccount) {
        this.toAccount = toAccount;
    }
}
