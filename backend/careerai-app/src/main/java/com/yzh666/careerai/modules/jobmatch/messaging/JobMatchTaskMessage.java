package com.yzh666.careerai.modules.jobmatch.messaging;

public record JobMatchTaskMessage(
    Long taskId,
    Long userId,
    Long resumeId,
    Long jobId,
    Integer attempt
) {
  public int safeAttempt() {
    return attempt == null ? 0 : attempt;
  }
}
