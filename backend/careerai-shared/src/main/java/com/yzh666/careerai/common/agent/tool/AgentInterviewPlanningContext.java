package com.yzh666.careerai.common.agent.tool;

import java.util.List;

/** Agent 创建新面试前读取的跨场次画像、任务和待验证项。 */
public record AgentInterviewPlanningContext(
    List<AgentAbilityProfileItem> abilityProfile,
    List<AgentInterviewImprovementTask> pendingTasks,
    List<String> recentUnverifiedTargets
) {}
