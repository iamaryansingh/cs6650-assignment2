package com.cs6650.client;

import com.cs6650.client.loadgen.LoadGenerator;
import com.cs6650.client.metrics.ClientMetrics;

public class TestClientApplication {
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("Usage: java -jar chat-test-client-1.0.0.jar <ws-url> <threads> <total-messages>");
      System.err.println("Example: java -jar chat-test-client-1.0.0.jar ws://localhost:8080/ws/chat 64 500000");
      System.exit(1);
    }

    String wsUrl = args[0];
    int threads = Integer.parseInt(args[1]);
    int totalMessages = Integer.parseInt(args[2]);

    ClientMetrics metrics = new ClientMetrics();
    LoadGenerator loadGenerator = new LoadGenerator(wsUrl, metrics);

    metrics.start();
    loadGenerator.run(threads, totalMessages);
    metrics.end();

    System.out.println(metrics.report());
  }
}
