package com.yzh666.careerai.modules.resumeplan.repository;

import com.yzh666.careerai.modules.resumeplan.model.ResumeImprovementPlanEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResumeImprovementPlanRepository extends JpaRepository<ResumeImprovementPlanEntity, Long> {

    List<ResumeImprovementPlanEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ResumeImprovementPlanEntity> findByUserIdAndMatchReportIdOrderByCreatedAtDesc(Long userId, Long matchReportId);

    Optional<ResumeImprovementPlanEntity> findFirstByUserIdAndMatchReportIdOrderByCreatedAtDesc(
        Long userId,
        Long matchReportId
    );

    Optional<ResumeImprovementPlanEntity> findByIdAndUserId(Long id, Long userId);

    Optional<ResumeImprovementPlanEntity> findByUserIdAndAgentIdempotencyKey(
        Long userId,
        String agentIdempotencyKey
    );
}
