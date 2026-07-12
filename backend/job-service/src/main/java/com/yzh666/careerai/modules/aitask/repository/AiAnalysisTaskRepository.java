package com.yzh666.careerai.modules.aitask.repository;

import com.yzh666.careerai.modules.aitask.model.AiAnalysisTaskEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisTaskRepository extends JpaRepository<AiAnalysisTaskEntity, Long> {

  Optional<AiAnalysisTaskEntity> findByIdAndUserId(Long id, Long userId);
}
