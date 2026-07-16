package com.yzh666.careerai.common.agent.tool;

import java.util.List;

/** 岗位匹配所需的简历内容与最近一次分析摘要。 */
public record AgentResumeDetail(
    Long id,
    String filename,
    String resumeText,
    String analyzeStatus,
    Integer latestScore,
    String latestSummary,
    List<String> strengths
) {
}
