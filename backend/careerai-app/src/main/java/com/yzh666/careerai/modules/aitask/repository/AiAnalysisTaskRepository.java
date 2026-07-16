package com.yzh666.careerai.modules.aitask.repository;

import com.yzh666.careerai.modules.aitask.model.AiAnalysisTaskEntity;
import com.yzh666.careerai.modules.aitask.model.AiAnalysisTaskType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisTaskRepository extends JpaRepository<AiAnalysisTaskEntity, Long> {

  Optional<AiAnalysisTaskEntity> findByIdAndUserId(Long id, Long userId);

  Optional<AiAnalysisTaskEntity> findByUserIdAndTaskTypeAndAgentIdempotencyKey(
      Long userId,
      AiAnalysisTaskType taskType,
      String agentIdempotencyKey
  );
}
