package com.cs6650.consumer.repository;

import com.cs6650.consumer.entity.DeadLetterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntity, Long> {
}
