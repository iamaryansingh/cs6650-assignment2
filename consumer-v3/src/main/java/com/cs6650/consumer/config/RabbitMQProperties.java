package com.cs6650.consumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rabbitmq")
public class RabbitMQProperties {
  private String host;
  private int port;
  private String username = "admin";
  private String password = "password123";
  private String virtualHost;
  private String queuePrefix;
  private int roomCount;
  private int prefetch;

  public String getHost() { return host; }
  public void setHost(String host) { this.host = host; }
  public int getPort() { return port; }
  public void setPort(int port) { this.port = port; }
  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
  public String getVirtualHost() { return virtualHost; }
  public void setVirtualHost(String virtualHost) { this.virtualHost = virtualHost; }
  public String getQueuePrefix() { return queuePrefix; }
  public void setQueuePrefix(String queuePrefix) { this.queuePrefix = queuePrefix; }
  public int getRoomCount() { return roomCount; }
  public void setRoomCount(int roomCount) { this.roomCount = roomCount; }
  public int getPrefetch() { return prefetch; }
  public void setPrefetch(int prefetch) { this.prefetch = prefetch; }
}
