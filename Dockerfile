FROM ghcr.io/graalvm/jdk:java17-22.3.1 AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -Pnative -f /usr/src/app/pom.xml clean package

FROM openjdk:17-oracle
EXPOSE 8080

ARG OTEL_OPTS
ENV OTEL_OPTS=${OTEL_OPTS}

ADD 'https://dtdg.co/latest-java-tracer' /usr/app/dd-java-agent.jar

COPY --from=builder /usr/src/app/target/remote-falcon-viewer app
ENTRYPOINT ["/app"]