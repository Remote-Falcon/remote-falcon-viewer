[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=remote-falcon_remote-falcon-viewer)](https://sonarcloud.io/summary/new_code?id=remote-falcon_remote-falcon-viewer)

# Remote Falcon Viewer

Remote Falcon Viewer is a Quarkus-based service that powers the viewer-facing APIs for Remote Falcon shows. It exposes REST and GraphQL endpoints used by clients to:
- View the current and next playing sequences
- Request sequences to be added to the jukebox queue
- Vote for sequences
- Track viewer activity and page statistics

The service uses MongoDB for persistence, supports reverse-proxy headers for client IP detection, and can be packaged as a JVM JAR or native binary.

- Tech stack: Quarkus, SmallRye GraphQL, RESTEasy, MongoDB
- Default HTTP root path: `/remote-falcon-viewer`

## Contents
- Overview
- API (REST + GraphQL)
- Configuration
- Local development
- Testing & coverage
- Build & packaging
- Docker & Kubernetes
- CI
- Troubleshooting

## Overview
The service manages and exposes viewer-related operations for a Remote Falcon show instance. It retrieves and updates data via a `ShowRepository` (MongoDB) and stores statistics about page views, voting, and jukebox requests. It also enforces rules such as request limits, geo-fencing (optional), and blocked IPs.

## API
All endpoints are served under the configured root path `/remote-falcon-viewer` (see quarkus.http.root-path in application.properties).

### REST endpoints
Base path: `/remote-falcon-viewer`

1) POST `/addSequenceToQueue`
- Request body (JSON):
  {
    "showSubdomain": "<string>",
    "sequence": "<string>",
    "viewerLatitude": <float|null>,
    "viewerLongitude": <float|null>
  }
- Response (JSON):
  { "message": "<optional error message>" }
- Behavior: Adds a sequence (or a sequence group) to the request queue subject to validation (IP presence, not blocked, not already requested, queue depth, geo rules, etc.). On success, returns an empty message.

2) POST `/voteForSequence`
- Request body (JSON):
  {
    "showSubdomain": "<string>",
    "sequence": "<string>",
    "viewerLatitude": <float|null>,
    "viewerLongitude": <float|null>
  }
- Response (JSON):
  { "message": "<optional error message>" }
- Behavior: Records a vote for a sequence or sequence group subject to validation rules. On success, returns an empty message.

Notes:
- Client IP is taken from standard proxy headers (CF-Connecting-IP, X-Forwarded-For, X-Real-IP, Forwarded) or the connection’s remote address.
- CORS is enabled by default for all origins.

### GraphQL endpoint
- URL: POST `/remote-falcon-viewer/graphql`
- Dev UI (dev mode): `http://localhost:8080/q/dev/` (only in dev; useful for exploring GraphQL schema)

Queries:
- getShow(showSubdomain: String): Show
- getActiveViewerPage(showSubdomain: String): String

Mutations:
- insertViewerPageStats(showSubdomain: String, date: DateTime): Boolean
- updateActiveViewers(showSubdomain: String): Boolean
- updatePlayingNow(showSubdomain: String, playingNow: String): Boolean
- updatePlayingNext(showSubdomain: String, playingNext: String): Boolean
- addSequenceToQueue(showSubdomain: String, name: String, latitude: Float, longitude: Float): Boolean
- voteForSequence(showSubdomain: String, name: String, latitude: Float, longitude: Float): Boolean

Example GraphQL request:
{
  "query": "mutation($sub:String!,$name:String!){ addSequenceToQueue(showSubdomain:$sub,name:$name,latitude:0,longitude:0) }",
  "variables": {"sub": "myshow", "name": "song-a"}
}

## Configuration
File: `src/main/resources/application.properties`
- quarkus.http.root-path=/remote-falcon-viewer
- quarkus.http.port=8080
- Proxy headers: allow-forwarded=true, proxy-address-forwarding=true, enable-forwarded-host=true, enable-forwarded-prefix=true
- MongoDB: quarkus.mongodb.database=remote-falcon
- Quarkus indexing for remote-falcon-library models
- OpenTelemetry: quarkus.otel.metrics.enabled=true, quarkus.application.name=remote-falcon-viewer
- CORS: enabled for all origins, methods, and headers
- Packaging: quarkus.package.jar.enabled=true (default for local builds; see Troubleshooting for native)

Environment variables (used mainly in Docker/K8s):
- MONGO_URI: MongoDB connection string (e.g., mongodb://user:pass@host:27017/remote-falcon?authSource=admin)
- OTEL_URI: OTLP endpoint for metrics/traces (if applicable)

## Local development
Requirements: JDK 17+, Gradle wrapper included.

- Start in dev mode (live reload):
  ./gradlew quarkusDev
  Dev UI: http://localhost:8080/q/dev/

- Run unit tests:
  ./gradlew test

- Generate coverage report (JaCoCo):
  ./gradlew test jacocoTestReport
  Open report at: build/reports/jacoco/test/html/index.html

## Build & packaging
- JVM JAR (default):
  ./gradlew clean build
  Runs tests and produces a fast-jar in build/quarkus-app/.

- Uber-jar:
  ./gradlew build -Dquarkus.package.jar.type=uber-jar

- Native image (local GraalVM):
  ./gradlew build -Dquarkus.native.enabled=true

- Native image (container build):
  ./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true

## Docker
A multi-stage Dockerfile is provided to build a native executable.

Build:
  docker build \
    --build-arg MONGO_URI="mongodb://user:pass@host:27017/remote-falcon?authSource=admin" \
    --build-arg OTEL_URI="http://otel-collector:4317" \
    -t remotefalcon/remote-falcon-viewer:latest .

Run:
  docker run -p 8080:8080 \
    -e MONGO_URI="mongodb://user:pass@host:27017/remote-falcon?authSource=admin" \
    -e OTEL_URI="http://otel-collector:4317" \
    remotefalcon/remote-falcon-viewer:latest

Notes:
- The Dockerfile disables JAR output during native builds to avoid conflicts (see Troubleshooting).
- The binary listens on 0.0.0.0:8080 by default.

## Kubernetes
Sample manifests are provided under `k8s/`:
- k8s/manifest.yml (cluster deploy)
- k8s/manifest-local.yml (local dev)

Ensure you set environment variables for MONGO_URI and OTEL_URI in your Deployment/ConfigMap/Secret. Expose port 8080. Respect the root path `/remote-falcon-viewer` in your Ingress routing.

## CI
There are GitHub Actions workflows under `.github/workflows/` (build, release, Sonar). They typically run tests, build artifacts, and can publish images/releases depending on configuration.

## Troubleshooting
- Error: Outputting both native and JAR packages is not currently supported.
  - Cause: Building native with JAR packaging enabled.
  - Fix: The project sets `quarkus.package.jar.enabled=true` for local builds. The Dockerfile explicitly passes `-Dquarkus.package.jar.enabled=false` during the native build stage to avoid dual outputs.

- GraphQL schema not visible:
  - Use dev mode: `./gradlew quarkusDev` and open Dev UI at `/q/dev/`.

- CORS/Proxy issues:
  - Verify proxy headers reach the service. Client IP is derived from `CF-Connecting-IP`, `X-Forwarded-For`, `X-Real-IP`, or `Forwarded` headers.

## Contributing
- Create feature branches and open pull requests.
- Ensure `./gradlew test` passes and add/update unit tests for changes.
