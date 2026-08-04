# Temporal + Dapr fraud detection POC

Wraps the Payment Service's internal flow (validate → fraud check → risk
decision → publish event) in a Temporal workflow. Dapr Pub/Sub is unchanged
in the sense that it's still the same component/broker — but the fraud
check itself is now **asynchronous**, matching the client's Temporal Flow
deck: Payment Service publishes a request and blocks on `Workflow.await()`;
Fraud Service consumes that request and **signals the workflow back
directly** via the Temporal `WorkflowClient`, rather than replying over
HTTP. Notification Service is untouched from the original assignment.

## Async fraud check (Signal-based)

1. Workflow calls `requestFraudCheckActivity.requestFraudCheck(request, workflowId)`
— fire-and-forget publish to Dapr Pub/Sub topic `fraud-check-requests`,
payload includes the workflow's own ID.
2. Workflow calls `Workflow.await(10s, () -> fraudRiskScore != null)` —
parks durably, does not busy-wait, survives a worker restart.
3. Fraud Service's `FraudCheckSubscriber` consumes that event, computes the
risk score with the same fixed rule, then calls
`workflowClient.newWorkflowStub(PaymentWorkflowSignalProxy.class, workflowId).receiveFraudResult(riskScore)`
— an external Signal call, not an HTTP response.
4. If no signal arrives within 10s, the workflow fails safe: `BLOCKED` /
`FRAUD\_SERVICE\_TIMEOUT`, still published so the audit trail isn't lost.

`PaymentWorkflowSignalProxy` in fraud-service is a minimal interface that
structurally matches the signal method on Payment Service's real workflow
interface — the two services don't share a JAR, which is realistic for
independently deployable microservices; Temporal only needs the signal
name to match.

The original synchronous `/fraud/check` endpoint (`FraudController`) is
still there but no longer on the live path — kept for direct testing of
the scoring rule with curl.

## Layout

```
temporal-dapr-poc/
├── docker-compose.yml          # postgres, temporal, temporal-ui, redis, 3 services + Dapr sidecars
├── dapr/components/pubsub.yaml # Redis pub/sub component (unchanged)
├── payment-service/            # Temporal workflow + activities + REST controller
├── fraud-service/               # unchanged: fixed scoring rule, Dapr Service Invocation target
├── notification-service/        # unchanged: Dapr Pub/Sub subscriber
└── test-script.sh
```

## What's actually new

`payment-service/src/main/java/com/bridgelab/payment/temporal/`:

* `PaymentProcessingWorkflow` / `PaymentProcessingWorkflowImpl` — the
workflow, including the `receiveFraudResult` signal and `Workflow.await`.
* `PaymentActivities` / `PaymentActivitiesImpl` — validate (local activity),
`requestFraudCheck` (remote activity, fire-and-forget Dapr Pub/Sub
publish), publish event (remote activity, Dapr Pub/Sub).
* `TemporalWorkerConfig` — starts the Temporal worker alongside the Spring
Boot app.

`fraud-service/src/main/java/com/bridgelab/fraud/`:

* `FraudCheckSubscriber` — the live fraud-check path: Dapr subscription +
signal-back.
* `temporal/PaymentWorkflowSignalProxy` — minimal signal-only interface.
* `temporal/TemporalClientConfig` — Temporal client wiring.
* `FraudController` — legacy synchronous endpoint, kept but unused by the flow.

`PaymentController` starts the workflow instead of doing the steps inline,
but the REST contract (`POST /v1/payments`) is unchanged.

## Running it

```bash
docker compose up --build
```

Wait until `temporal` and all three app containers report healthy/running
(first boot pulls a few images and runs Temporal's schema setup — give it
30–60s). Then:

```bash
chmod +x test-script.sh
./test-script.sh
```

Check the results:

* **Notification Service logs** (audit trail): `docker compose logs notification-service`
* **Temporal UI** (workflow execution history, retries, timing): http://localhost:8233
* **Temporal frontend gRPC**: localhost:7233 (used by payment-service internally)

## Observability: tracing and metrics

Three new containers, one Grafana dashboard:

* **Tempo** (`tempo:4317` OTLP gRPC / `:4318` OTLP HTTP) — trace storage.
* **Prometheus** (`prometheus:9090`) — scrapes Temporal, all three Dapr
sidecars, and all three Spring Boot actuator endpoints (see
`observability/prometheus.yml`).
* **Grafana** (http://localhost:3000, anonymous admin access for the POC) —
provisioned on startup with both datasources and one dashboard,
`Payment Pipeline Overview`: request rate/latency, Temporal workflow
completions and activity latency, Dapr pub/sub and sidecar latency, JVM
heap per service, and a Tempo trace-search panel for `payment-service`.

What changed to wire this up:

* **Dapr**: added `dapr/components/tracing-config.yaml` (a `Configuration`
resource, 100% sampling, OTLP to `tempo:4317`) and added
`-config /components/tracing-config.yaml` to all three `daprd` commands
in `docker-compose.yml`. Note: in self-hosted mode `-config` takes a
literal file path, not the resource's `metadata.name` (that convention
is Kubernetes-mode only) — passing just `tracing-config` fails with
`stat tracing-config: no such file or directory`. Dapr sidecars already
expose Prometheus metrics on `:9090` by default — no change needed there
beyond adding the scrape target.
* **Temporal**: added `PROMETHEUS\_ENDPOINT: 0.0.0.0:9090` to the `temporal`
container's environment.
* **All three Spring Boot services**: added `spring-boot-starter-actuator`,
`micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`, and
`opentelemetry-exporter-otlp` to each `pom.xml`; added
`management.endpoints.web.exposure.include=health,prometheus`,
`management.tracing.sampling.probability=1.0`, and
`management.otlp.tracing.endpoint=http://tempo:4318/v1/traces` to each
`application.properties`. This is Spring Boot 3.x's native Micrometer
Tracing + OTLP support — no manual OpenTelemetry SDK wiring needed.
* New `observability/` directory: `prometheus.yml`, `tempo.yaml`, and
`grafana/provisioning/` (datasources + the one dashboard JSON).

**Caveat**: the exact Temporal and Dapr metric names in the dashboard
(`temporal\_workflow\_completed\_total`, `dapr\_http\_server\_response\_latency\_seconds\_bucket`,
etc.) are the standard names for these versions, but weren't verified
against a live scrape in this sandbox (no Docker network access here). If a
panel comes up empty, check Prometheus's own UI at
http://localhost:9091/targets to confirm the target is being scraped, then
http://localhost:9091/graph to find the actual metric name and adjust the
panel's PromQL.

**Trace correlation across services**: Dapr propagates the W3C
`traceparent` header automatically across Pub/Sub messages once tracing is
enabled, so a single trace should span Payment Service → Dapr → Fraud
Service's subscriber → its signal call. Spring Boot's Micrometer Tracing
picks up and continues an incoming `traceparent` on inbound HTTP requests
automatically. Worth confirming this end-to-end in the Tempo panel once
running — cross-process context propagation through Dapr's Pub/Sub is the
one part of this chain most worth double-checking live.

* `PaymentResult` unifies `processed\_at`/`blocked\_at` from the original spec
into a single `timestamp` field. Trivial to split back into two field
names if a downstream consumer needs the exact original contract.
* No Dapr State Store / idempotency check yet — that's the assignment's own
"Challenges (After Session)" item and slots in naturally as a follow-up:
the workflow's `publishEvent` failure path is exactly where you'd check/set
an idempotency key.
* No CloudEvents schema enforcement on the pub/sub payload — same as the
base assignment, listed as a later challenge there too.
* The Signal pattern uses this project's existing Redis-backed Dapr Pub/Sub
component rather than raw Kafka. Dapr abstracts the broker, so swapping
`pubsub.redis` for `pubsub.kafka` in `dapr/components/pubsub.yaml` is a
config change, not a code change, if you want it on real Kafka.
* If a fraud-check-requests event is redelivered after its workflow has
already timed out or completed, `newWorkflowStub(...).receiveFraudResult()`
throws; `FraudCheckSubscriber` catches and logs it rather than failing
the Dapr delivery. Fine for a POC; a production version would want to
distinguish "already completed" from "transient Temporal error" so it
doesn't swallow the latter.

## Startup-race fix

The first version of this compose file had two bugs that surfaced as
`UnknownHostException: temporal` and Temporal itself crashing with
`Unable to create dynamic config client`:

1. `DYNAMIC\_CONFIG\_FILE\_PATH` was pointed at a file that doesn't exist in
the `auto-setup` image, which made the Temporal server fail to start.
Fixed by removing that override and letting the image's own entrypoint
set it.
2. `depends\_on` only waits for a container to *start*, not for the service
inside it to be *ready* — so Payment/Fraud Service could dial Temporal
before its frontend was actually listening. Fixed with
`wait-for-temporal.sh`, copied into both services' Docker images, which
blocks on a raw TCP connection to `TEMPORAL\_SERVICE\_ADDRESS` before
`exec`-ing the JVM.

## Not built/tested in-sandbox

This was written directly to file, not compiled or run here — the sandbox's
network allowlist doesn't include Maven Central, so `mvn package` can't
resolve dependencies in this environment. Everything is written against
Spring Boot 3.2.5 / Temporal Java SDK 1.24.1 / Dapr Java SDK 1.11.0 APIs,
but run `docker compose up --build` locally as the real verification step.
If a version pin causes a resolution issue, bumping `temporal.version` or
`dapr.version` in the two `pom.xml` files is the first thing to try.



changes,,

