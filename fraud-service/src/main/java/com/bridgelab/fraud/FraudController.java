package com.bridgelab.fraud;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * LEGACY / not on the live path. This is the original synchronous
 * Dapr-Service-Invocation endpoint from the base assignment. The workflow
 * no longer calls this -- it now goes through FraudCheckSubscriber's async
 * Dapr Pub/Sub + Temporal Signal path instead. Left in place since it's
 * harmless and useful for testing the scoring rule directly with curl.
 */
@RestController
@RequestMapping("/fraud")
public class FraudController {

    private static final double AMOUNT_THRESHOLD = 10000.0;
    private static final double HIGH_RISK_SCORE = 0.95;
    private static final double LOW_RISK_SCORE = 0.15;

    @PostMapping("/check")
    public Map<String, Object> check(@RequestBody Map<String, Object> body) {
        double amount = ((Number) body.get("amount")).doubleValue();
        double riskScore = amount > AMOUNT_THRESHOLD ? HIGH_RISK_SCORE : LOW_RISK_SCORE;

        Map<String, Object> response = new HashMap<>();
        response.put("riskScore", riskScore);
        return response;
    }
}
