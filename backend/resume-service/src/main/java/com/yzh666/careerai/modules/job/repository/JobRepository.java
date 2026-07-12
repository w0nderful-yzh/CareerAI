package com.yzh666.careerai.modules.job.repository;

import com.yzh666.careerai.modules.job.model.JobEntity;
import com.yzh666.careerai.modules.job.model.JobStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<JobEntity, Long> {

    List<JobEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<JobEntity> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, JobStatus status);

    Optional<JobEntity> findByIdAndUserId(Long id, Long userId);
}
