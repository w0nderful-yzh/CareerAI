package com.yzh666.careerai.modules.interview.repository;

import com.yzh666.careerai.modules.interview.model.AbilityProfileEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbilityProfileRepository extends JpaRepository<AbilityProfileEntity, Long> {

  Optional<AbilityProfileEntity> findByUserIdAndDimensionAndAbilityKey(
      Long userId,
      String dimension,
      String abilityKey
  );

  List<AbilityProfileEntity> findByUserIdOrderByLastObservedAtDesc(Long userId);
}
