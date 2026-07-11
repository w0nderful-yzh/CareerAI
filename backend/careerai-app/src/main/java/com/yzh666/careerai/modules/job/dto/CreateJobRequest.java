package com.yzh666.careerai.modules.job.dto;

import com.yzh666.careerai.modules.interview.skill.InterviewSkillService.CategoryDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateJobRequest(
    @Size(max = 160, message = "岗位名称最多 160 个字符")
    String title,

    @Size(max = 160, message = "公司名称最多 160 个字符")
    String company,

    @Size(max = 120, message = "地点最多 120 个字符")
    String location,

    @Size(max = 500, message = "来源链接最多 500 个字符")
    String sourceUrl,

    @NotBlank(message = "JD 内容不能为空")
    String jdText,

    List<CategoryDTO> parsedCategories
) {
}
