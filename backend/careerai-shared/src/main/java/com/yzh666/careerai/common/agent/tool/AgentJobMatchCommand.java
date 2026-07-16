package com.yzh666.careerai.common.agent.tool;

/** 启动岗位匹配任务的 Tool 入参。用户身份只从 Authorization 获取，不允许模型传 userId。 */
public record AgentJobMatchCommand(
    Long resumeId,
    Long jobId
) {
}
