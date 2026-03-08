package com.cs6650.server.config;

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
  private String exchange;
  private String queuePrefix;
  private int roomCount;
  private int channelPoolSize;
  private int publisherTimeoutMs;
  private int queueMessageTtlMs;
  private int queueMaxLength;

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
  public String getExchange() { return exchange; }
  public void setExchange(String exchange) { this.exchange = exchange; }
  public String getQueuePrefix() { return queuePrefix; }
  public void setQueuePrefix(String queuePrefix) { this.queuePrefix = queuePrefix; }
  public int getRoomCount() { return roomCount; }
  public void setRoomCount(int roomCount) { this.roomCount = roomCount; }
  public int getChannelPoolSize() { return channelPoolSize; }
  public void setChannelPoolSize(int channelPoolSize) { this.channelPoolSize = channelPoolSize; }
  public int getPublisherTimeoutMs() { return publisherTimeoutMs; }
  public void setPublisherTimeoutMs(int publisherTimeoutMs) { this.publisherTimeoutMs = publisherTimeoutMs; }
  public int getQueueMessageTtlMs() { return queueMessageTtlMs; }
  public void setQueueMessageTtlMs(int queueMessageTtlMs) { this.queueMessageTtlMs = queueMessageTtlMs; }
  public int getQueueMaxLength() { return queueMaxLength; }
  public void setQueueMaxLength(int queueMaxLength) { this.queueMaxLength = queueMaxLength; }
}
