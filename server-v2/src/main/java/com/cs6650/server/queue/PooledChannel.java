package com.cs6650.server.queue;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a RabbitMQ Channel with async publisher confirms and a throttle semaphore.
 *
 * Why async confirms instead of waitForConfirmsOrDie per message:
 *   waitForConfirmsOrDie blocks the calling thread until RabbitMQ ACKs the disk write.
 *   On a t3.small with throttled EBS, each ACK round-trip takes ~1-5ms, capping each channel
 *   at ~200-1000 msg/s. With async confirms the thread publishes and returns immediately;
 *   the semaphore only blocks when MAX_OUTSTANDING unconfirmed messages pile up, which
 *   practically never happens under normal load. This decouples publish rate from ACK rate.
 *
 * Why track sequence numbers with ConcurrentSkipListMap:
 *   RabbitMQ can send cumulative ACKs (multiple=true) covering all messages up to seqNo N.
 *   Without seq tracking we'd leak semaphore permits (under-releasing) and eventually starve.
 *   The map lets us count exactly how many messages are being confirmed in each callback.
 */
public class PooledChannel {

  private static final Logger log = LoggerFactory.getLogger(PooledChannel.class);
  private static final int MAX_OUTSTANDING = 500;

  private final Channel channel;
  private final Semaphore throttle;
  private final ConcurrentSkipListMap<Long, Boolean> pending;

  public PooledChannel(Channel channel) {
    this.channel = channel;
    this.throttle = new Semaphore(MAX_OUTSTANDING);
    this.pending = new ConcurrentSkipListMap<>();

    channel.addConfirmListener(
        (seqNo, multiple) -> handleAck(seqNo, multiple),
        (seqNo, multiple) -> handleNack(seqNo, multiple));
  }

  /**
   * Publish a message asynchronously. Blocks only if MAX_OUTSTANDING confirms are in flight.
   * The caller does NOT wait for a RabbitMQ ACK — the confirm listener handles that
   * asynchronously.
   */
  public void publish(String exchange, String routingKey,
      AMQP.BasicProperties props, byte[] body) throws IOException, InterruptedException {
    throttle.acquire();
    long seqNo = channel.getNextPublishSeqNo();
    pending.put(seqNo, Boolean.TRUE);
    try {
      channel.basicPublish(exchange, routingKey, true, props, body);
    } catch (IOException e) {
      // Publish failed before it reached RabbitMQ: clean up the pending entry and
      // release the semaphore so the channel stays usable.
      pending.remove(seqNo);
      throttle.release();
      throw e;
    }
  }

  public Channel channel() {
    return channel;
  }

  public boolean isOpen() {
    return channel.isOpen();
  }

  private void handleAck(long seqNo, boolean multiple) {
    int count = removeConfirmed(seqNo, multiple);
    throttle.release(count);
  }

  private void handleNack(long seqNo, boolean multiple) {
    int count = removeConfirmed(seqNo, multiple);
    throttle.release(count);
    log.warn("RabbitMQ NACK: seqNo={} multiple={} — message(s) NOT stored by broker", seqNo, multiple);
  }

  /**
   * Removes confirmed sequence numbers from pending and returns how many were removed.
   * multiple=true means all messages up to seqNo are confirmed (cumulative ACK).
   */
  private int removeConfirmed(long seqNo, boolean multiple) {
    if (multiple) {
      var head = pending.headMap(seqNo, true);
      int size = head.size();
      head.clear();
      // If size is 0 (e.g., confirms arrived before puts in rare race), release at least 1
      // to avoid a stuck semaphore.
      return Math.max(1, size);
    } else {
      pending.remove(seqNo);
      return 1;
    }
  }
}
