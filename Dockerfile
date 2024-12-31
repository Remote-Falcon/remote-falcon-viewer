# FROM container-registry.oracle.com/graalvm/native-image:17.0.12 AS build
# COPY src /usr/src/app/src
# COPY pom.xml /usr/src/app
# RUN mvn -f /usr/src/app/pom.xml clean package
#
# FROM openjdk:17-oracle
# COPY --from=build /usr/src/app/target/remote-falcon-viewer.jar /usr/app/remote-falcon-viewer.jar
# EXPOSE 8080
#
# ARG OTEL_OPTS
# ENV OTEL_OPTS=${OTEL_OPTS}
#
# ADD 'https://dtdg.co/latest-java-tracer' /usr/app/dd-java-agent.jar
#
# ENTRYPOINT exec java $JAVA_OPTS $OTEL_OPTS -XX:FlightRecorderOptions=stackdepth=256 -XX:MaxRAMPercentage=90.0 -jar /usr/app/remote-falcon-viewer.jar

FROM container-registry.oracle.com/graalvm/native-image:21-ol8 AS builder
WORKDIR /build
COPY . /build
RUN ./mvnw --no-transfer-progress native:compile -Pnative

# Stage 2: Run the native image
FROM ubuntu:22.04
# Install necessary dependencies
RUN apt-get update && apt-get install -y \
    libc6 \
    libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

# The deployment Image
FROM container-registry.oracle.com/os/oraclelinux:8-slim
EXPOSE 8080

# Copy the native executable into the containers
COPY --from=builder /build/target/remote-falcon-viewer app
ENTRYPOINT ["/app"]