package com.bridgelab.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Dapr Pub/Sub subscriber. This class has no dependency on Temporal at
 * all -- it doesn't know or care that Payment Service now runs its logic
 * inside a workflow. It only knows there's an event on payment-events.
 */
@RestController
public class NotificationController {

    @GetMapping("/dapr/subscribe")
    public List<Map<String, String>> subscribe() {
        Map<String, String> subscription = new HashMap<>();
        subscription.put("pubsubname", "payment-pubsub");
        subscription.put("topic", "payment-events");
        subscription.put("route", "/payment-events");
        return List.of(subscription);
    }

    @PostMapping("/payment-events")
    public ResponseEntity<Void> handleEvent(@RequestBody Map<String, Object> cloudEvent) {
        Object data = cloudEvent.get("data");
        System.out.println("[notification-service] payment event received: " + data);
        return ResponseEntity.ok().build();
    }
}
