package com.cs6650.server.repository;

import com.cs6650.server.entity.ChatMessageEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<ChatMessageEntity, Long> {

  // Core Query 1
  List<ChatMessageEntity> findByRoomIdAndTimestampBetweenOrderByTimestampAsc(
      String roomId, Instant startTime, Instant endTime);

  // Core Query 2
  List<ChatMessageEntity> findByUserIdOrderByTimestampDesc(String userId);

  List<ChatMessageEntity> findByUserIdAndTimestampBetweenOrderByTimestampDesc(
      String userId, Instant startTime, Instant endTime);

  // Core Query 3
  @Query("SELECT COUNT(DISTINCT m.userId) FROM ChatMessageEntity m " +
         "WHERE m.timestamp BETWEEN :start AND :end")
  Long countDistinctUsersByTimestampBetween(
      @Param("start") Instant start, @Param("end") Instant end);

  // Core Query 4
  @Query("SELECT m.roomId, MAX(m.timestamp), COUNT(m) FROM ChatMessageEntity m " +
         "WHERE m.userId = :userId GROUP BY m.roomId ORDER BY MAX(m.timestamp) DESC")
  List<Object[]> findRoomsWithActivityByUserId(@Param("userId") String userId);

  // Analytics: top N users
  @Query(value = "SELECT user_id, username, COUNT(*) AS cnt FROM messages " +
                 "GROUP BY user_id, username ORDER BY cnt DESC LIMIT :n",
         nativeQuery = true)
  List<Object[]> findTopUsers(@Param("n") int n);

  // Analytics: top N rooms
  @Query(value = "SELECT room_id, COUNT(*) AS cnt, COUNT(DISTINCT user_id) AS unique_users " +
                 "FROM messages GROUP BY room_id ORDER BY cnt DESC LIMIT :n",
         nativeQuery = true)
  List<Object[]> findTopRooms(@Param("n") int n);

  // Analytics: messages per second
  @Query(value = "SELECT date_trunc('second', timestamp) AS ts, COUNT(*) AS cnt " +
                 "FROM messages GROUP BY ts ORDER BY ts",
         nativeQuery = true)
  List<Object[]> getMessagesPerSecond();

  // Analytics: user hourly pattern
  @Query(value = "SELECT EXTRACT(HOUR FROM timestamp) AS hour, COUNT(*) AS cnt " +
                 "FROM messages WHERE user_id = :userId GROUP BY hour ORDER BY hour",
         nativeQuery = true)
  List<Object[]> getUserHourlyPattern(@Param("userId") String userId);

  // Total message count (inherited from JpaRepository as count())
}
