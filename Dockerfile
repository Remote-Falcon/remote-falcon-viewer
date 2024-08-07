FROM maven:3-openjdk-17-slim AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml clean package

FROM openjdk:17-oracle
COPY --from=build /usr/src/app/target/remote-falcon-viewer.jar /usr/app/remote-falcon-viewer.jar
EXPOSE 8080

ARG OTEL_OPTS
ENV OTEL_OPTS=${OTEL_OPTS}

ADD 'https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar' /usr/app/opentelemetry-javaagent.jar

ENTRYPOINT exec java $JAVA_OPTS $OTEL_OPTS -XX:FlightRecorderOptions=stackdepth=256 -jar /usr/app/remote-falcon-viewer.jar