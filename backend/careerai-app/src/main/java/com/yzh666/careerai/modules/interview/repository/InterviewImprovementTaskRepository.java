package com.yzh666.careerai.modules.interview.repository;

import com.yzh666.careerai.modules.interview.model.InterviewImprovementTaskEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewImprovementTaskRepository
    extends JpaRepository<InterviewImprovementTaskEntity, Long> {

  boolean existsByIdempotencyKey(String idempotencyKey);

  List<InterviewImprovementTaskEntity> findBySessionIdOrderByIdAsc(String sessionId);

  List<InterviewImprovementTaskEntity> findTop10ByUserIdAndStatusOrderByCreatedAtDesc(
      Long userId,
      String status
  );
}
