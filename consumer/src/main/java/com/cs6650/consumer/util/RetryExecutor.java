package com.cs6650.consumer.util;

import java.util.function.IntSupplier;

public final class RetryExecutor {
  private RetryExecutor() {
  }

  public static boolean run(IntSupplier attemptAction, int maxAttempts) {
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      int status = attemptAction.getAsInt();
      if (status == 1) {
        return true;
      }
    }
    return false;
  }
}
