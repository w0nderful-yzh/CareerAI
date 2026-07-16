package com.yzh666.careerai.modules.interview.repository;

import com.yzh666.careerai.modules.interview.model.AbilityObservationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbilityObservationRepository
    extends JpaRepository<AbilityObservationEntity, Long> {

  boolean existsByEvidenceIdAndDimensionAndAbilityKey(
      Long evidenceId,
      String dimension,
      String abilityKey
  );

  List<AbilityObservationEntity> findByUserIdAndDimensionAndAbilityKeyOrderByObservedAtAsc(
      Long userId,
      String dimension,
      String abilityKey
  );

  Optional<AbilityObservationEntity> findByIdAndUserId(Long id, Long userId);
}
