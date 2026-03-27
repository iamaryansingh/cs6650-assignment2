package com.cs6650.consumer.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "consumer")
public class ConsumerProperties {
  private int workerCount;
  private String serverEndpoints;
  private int requestTimeoutMs;
  private int maxRetries;
  private long dedupeTtlMs;

  public int getWorkerCount() { return workerCount; }
  public void setWorkerCount(int workerCount) { this.workerCount = workerCount; }
  public String getServerEndpoints() { return serverEndpoints; }
  public void setServerEndpoints(String serverEndpoints) { this.serverEndpoints = serverEndpoints; }
  public int getRequestTimeoutMs() { return requestTimeoutMs; }
  public void setRequestTimeoutMs(int requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }
  public int getMaxRetries() { return maxRetries; }
  public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
  public long getDedupeTtlMs() { return dedupeTtlMs; }
  public void setDedupeTtlMs(long dedupeTtlMs) { this.dedupeTtlMs = dedupeTtlMs; }

  public List<String> endpointList() {
    return Arrays.stream(serverEndpoints.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
  }
}
