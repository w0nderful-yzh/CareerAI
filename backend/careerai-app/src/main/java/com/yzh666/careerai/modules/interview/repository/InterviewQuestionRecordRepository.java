package com.yzh666.careerai.modules.interview.repository;

import com.yzh666.careerai.modules.interview.model.InterviewQuestionRecordEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuestionRecordRepository
    extends JpaRepository<InterviewQuestionRecordEntity, Long> {

  Optional<InterviewQuestionRecordEntity> findBySession_SessionIdAndQuestionIndex(
      String sessionId,
      Integer questionIndex
  );
}
