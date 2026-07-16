package com.yzh666.careerai.common.agent.tool;

import java.util.List;

/** Python Agent 创建面试会话时提交给 Java 的受控命令。 */
public record AgentCreateInterviewSessionCommand(
    String resumeText,
    int questionCount,
    Long resumeId,
    Boolean forceCreate,
    String llmProvider,
    String skillId,
    String difficulty,
    List<AgentInterviewCategory> customCategories,
    String jdText,
    Long jobId,
    Long matchReportId,
    AgentInterviewBlueprint blueprint
) {}
