package com.yzh666.careerai.common.agent.tool;

import java.time.LocalDateTime;
import java.util.List;

/** Agent 后续制定行动计划时使用的岗位匹配证据。 */
public record AgentJobMatchReport(
    Long id,
    Long resumeId,
    String resumeFilename,
    Long jobId,
    String jobTitle,
    Integer overallScore,
    Integer skillScore,
    Integer projectScore,
    Integer keywordScore,
    String summary,
    List<String> matchedHighlights,
    List<String> gaps,
    List<String> actionItems,
    List<AgentRequirementEvidence> evidenceMappings,
    LocalDateTime createdAt
) {
}
