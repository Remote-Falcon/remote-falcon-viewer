package com.remotefalcon.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Quarkus test resource that provides a MongoDB Testcontainer for integration tests.
 * This ensures tests can run without requiring a local MongoDB installation.
 */
public class MongoTestResource implements QuarkusTestResourceLifecycleManager {

  private MongoDBContainer mongoContainer;

  @Override
  public Map<String, String> start() {
    mongoContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0"))
        .withReuse(true);

    mongoContainer.start();

    // Override the MongoDB connection string for tests
    return Map.of(
        "quarkus.mongodb.connection-string", mongoContainer.getReplicaSetUrl()
    );
  }

  @Override
  public void stop() {
    if (mongoContainer != null) {
      mongoContainer.stop();
    }
  }
}
