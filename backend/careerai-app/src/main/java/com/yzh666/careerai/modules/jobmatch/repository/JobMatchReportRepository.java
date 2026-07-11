package com.yzh666.careerai.modules.jobmatch.repository;

import com.yzh666.careerai.modules.jobmatch.model.JobMatchReportEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMatchReportRepository extends JpaRepository<JobMatchReportEntity, Long> {

    List<JobMatchReportEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<JobMatchReportEntity> findByUserIdAndJobIdOrderByCreatedAtDesc(Long userId, Long jobId);

    Optional<JobMatchReportEntity> findByIdAndUserId(Long id, Long userId);
}
