package com.yzh666.careerai.modules.job.dto;

import com.yzh666.careerai.modules.interview.skill.InterviewSkillService.CategoryDTO;
import com.yzh666.careerai.modules.job.model.JobStatus;
import java.time.LocalDateTime;
import java.util.List;

public record JobDTO(
    Long id,
    String title,
    String company,
    String location,
    String sourceUrl,
    JobStatus status,
    String jdText,
    List<CategoryDTO> parsedCategories,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
