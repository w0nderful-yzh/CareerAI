package com.yzh666.careerai.modules.job.dto;

import com.yzh666.careerai.modules.interview.skill.InterviewSkillService.CategoryDTO;
import java.util.List;

public record JobParseResponse(
    String suggestedTitle,
    List<CategoryDTO> categories
) {
}
