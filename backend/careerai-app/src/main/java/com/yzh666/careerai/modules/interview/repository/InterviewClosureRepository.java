package com.yzh666.careerai.modules.interview.repository;

import com.yzh666.careerai.modules.interview.model.InterviewClosureEntity;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewClosureRepository extends JpaRepository<InterviewClosureEntity, Long> {

  Optional<InterviewClosureEntity> findBySessionId(String sessionId);

  Optional<InterviewClosureEntity> findBySessionIdAndUserId(String sessionId, Long userId);

  List<InterviewClosureEntity> findTop5ByUserIdOrderByGeneratedAtDesc(Long userId);
}
