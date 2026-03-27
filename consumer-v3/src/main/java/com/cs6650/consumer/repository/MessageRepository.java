package com.cs6650.consumer.repository;

import com.cs6650.consumer.entity.ChatMessageEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<ChatMessageEntity, Long> {

  // ================================================================
  // CORE QUERY 1: Room messages in time range  (target < 100ms)
  // Uses idx_messages_room_timestamp
  // ================================================================
  List<ChatMessageEntity> findByRoomIdAndTimestampBetweenOrderByTimestampAsc(
      String roomId, Instant startTime, Instant endTime);

  // ================================================================
  // CORE QUERY 2: User message history  (target < 200ms)
  // Uses idx_messages_user_timestamp
  // ================================================================
  List<ChatMessageEntity> findByUserIdOrderByTimestampDesc(String userId);

  List<ChatMessageEntity> findByUserIdAndTimestampBetweenOrderByTimestampDesc(
      String userId, Instant startTime, Instant endTime);

  // ================================================================
  // CORE QUERY 3: Count active users in time window  (target < 500ms)
  // Uses idx_messages_timestamp
  // ================================================================
  @Query("SELECT COUNT(DISTINCT m.userId) FROM ChatMessageEntity m " +
         "WHERE m.timestamp BETWEEN :start AND :end")
  Long countDistinctUsersByTimestampBetween(
      @Param("start") Instant start,
      @Param("end") Instant end);

  // ================================================================
  // CORE QUERY 4: Rooms a user participated in  (target < 50ms)
  // Uses idx_messages_user_timestamp
  // ================================================================
  @Query("SELECT m.roomId, MAX(m.timestamp), COUNT(m) FROM ChatMessageEntity m " +
         "WHERE m.userId = :userId GROUP BY m.roomId ORDER BY MAX(m.timestamp) DESC")
  List<Object[]> findRoomsWithActivityByUserId(@Param("userId") String userId);

  // ================================================================
  // ANALYTICS: Top N users by message count
  // ================================================================
  @Query(value = "SELECT user_id, username, COUNT(*) AS cnt FROM messages " +
                 "GROUP BY user_id, username ORDER BY cnt DESC LIMIT :n",
         nativeQuery = true)
  List<Object[]> findTopUsers(@Param("n") int n);

  // ================================================================
  // ANALYTICS: Top N rooms by message count
  // ================================================================
  @Query(value = "SELECT room_id, COUNT(*) AS cnt, COUNT(DISTINCT user_id) AS unique_users " +
                 "FROM messages GROUP BY room_id ORDER BY cnt DESC LIMIT :n",
         nativeQuery = true)
  List<Object[]> findTopRooms(@Param("n") int n);

  // ================================================================
  // ANALYTICS: Messages per second (for throughput stats)
  // ================================================================
  @Query(value = "SELECT date_trunc('second', timestamp) AS ts, COUNT(*) AS cnt " +
                 "FROM messages GROUP BY ts ORDER BY ts",
         nativeQuery = true)
  List<Object[]> getMessagesPerSecond();

  // ================================================================
  // ANALYTICS: Hourly distribution for a user
  // ================================================================
  @Query(value = "SELECT EXTRACT(HOUR FROM timestamp) AS hour, COUNT(*) AS cnt " +
                 "FROM messages WHERE user_id = :userId GROUP BY hour ORDER BY hour",
         nativeQuery = true)
  List<Object[]> getUserHourlyPattern(@Param("userId") String userId);

  // ================================================================
  // WRITE: Idempotent batch insert — ON CONFLICT (message_id) DO NOTHING
  // Used by BatchMessageWriter for safe duplicate handling
  // ================================================================
  @Modifying
  @Query(value =
      "INSERT INTO messages " +
      "(message_id, room_id, user_id, username, message, timestamp, message_type, server_id, client_ip, processed_at) " +
      "VALUES (:#{#e.messageId}, :#{#e.roomId}, :#{#e.userId}, :#{#e.username}, :#{#e.message}, " +
      ":#{#e.timestamp}, :#{#e.messageType}, :#{#e.serverId}, :#{#e.clientIp}, NOW()) " +
      "ON CONFLICT (message_id) DO NOTHING",
      nativeQuery = true)
  int upsertIgnoreDuplicate(@Param("e") ChatMessageEntity e);
}
