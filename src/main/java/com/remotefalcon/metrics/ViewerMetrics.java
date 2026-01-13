package com.remotefalcon.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ViewerMetrics {

  private final Counter requestCounter;
  private final Counter voteCounter;

  public ViewerMetrics(MeterRegistry registry) {
    this.requestCounter = Counter.builder("viewer_requests_total")
        .description("Total successful viewer requests")
        .register(registry);
    this.voteCounter = Counter.builder("viewer_votes_total")
        .description("Total successful viewer votes")
        .register(registry);
  }

  public void recordRequestSuccess() {
    requestCounter.increment();
  }

  public void recordVoteSuccess() {
    voteCounter.increment();
  }
}
