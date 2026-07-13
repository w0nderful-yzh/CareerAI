package com.yzh666.careerai.modules.llmprovider.repository;

import com.yzh666.careerai.modules.llmprovider.model.LlmGlobalSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmGlobalSettingRepository extends JpaRepository<LlmGlobalSettingEntity, Long> {
}
