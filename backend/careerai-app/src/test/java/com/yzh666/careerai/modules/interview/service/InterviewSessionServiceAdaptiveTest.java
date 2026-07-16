package com.yzh666.careerai.modules.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnEvaluation;
import com.yzh666.careerai.common.agent.tool.AgentNextQuestionIntent;
import com.yzh666.careerai.common.ai.LlmProviderRegistry;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.infrastructure.redis.InterviewSessionCache;
import com.yzh666.careerai.infrastructure.redis.InterviewSessionCache.CachedSession;
import com.yzh666.careerai.modules.interview.listener.EvaluateStreamProducer;
import com.yzh666.careerai.modules.interview.model.InterviewQuestionDTO;
import com.yzh666.careerai.modules.interview.model.InterviewBlueprintDTO;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity.CompletionType;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity.EndReason;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity;
import com.yzh666.careerai.modules.interview.model.InterviewSessionDTO.SessionStatus;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceAdaptiveTest {

  @Mock
  private InterviewQuestionService questionService;
  @Mock
  private AnswerEvaluationService evaluationService;
  @Mock
  private InterviewPersistenceService persistenceService;
  @Mock
  private InterviewSessionCache sessionCache;
  @Mock
  private EvaluateStreamProducer evaluateStreamProducer;
  @Mock
  private LlmProviderRegistry llmProviderRegistry;
  @Mock
  private JobMatchService jobMatchService;
  @Mock
  private AbilityProfileService abilityProfileService;
  @Mock
  private InterviewClosureService interviewClosureService;

  private ObjectMapper objectMapper;
  private InterviewSessionService service;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    service = new InterviewSessionService(
        questionService,
        evaluationService,
        persistenceService,
        sessionCache,
        objectMapper,
        evaluateStreamProducer,
        llmProviderRegistry,
        jobMatchService,
        abilityProfileService,
        interviewClosureService
    );
  }

  @Test
  void generatesAndAppendsFollowUpFromControlledIntent() {
    stubIncrementalSession();
    when(persistenceService.getAgentDecisions("session-1")).thenReturn(List.of());
    when(persistenceService.appendAgentDecision(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(persistenceService.getCustomCategories("session-1")).thenReturn(List.of());
    when(jobMatchService.buildInterviewContext(null, null, null)).thenReturn("");
    when(questionService.generateNextQuestion(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(InterviewQuestionDTO.create(
            1, "缓存更新如何保证一致性？", "PROJECT_EVIDENCE", "Redis 一致性",
            "Redis 一致性", true, 0, "REQ-1"));

    var result = service.applyAdaptiveTurn(
        "session-1",
        new AgentInterviewTurnCommand(
            0,
            "通过缓存空值和布隆过滤器处理。",
            "FOLLOW_UP",
            "基础方案正确，需要继续验证一致性处理。",
            72,
            "覆盖缓存穿透，但没有说明更新一致性。",
            "KEEP",
            nextIntent(true),
            evaluation("缓存空值"),
            null,
            "ANSWER"
        )
    );

    assertFalse(result.completed());
    assertEquals(1, result.nextQuestion().questionIndex());
    assertEquals("REQ-1", result.decision().targetRequirementId());
    verify(persistenceService).appendQuestion(any(), any(), any(), any());
    verify(persistenceService).updateCurrentQuestionIndex("session-1", 1);
    verify(persistenceService).saveAdaptiveTurn(
        "session-1",
        0,
        "如何处理缓存穿透？",
        "Redis",
        "通过缓存空值和布隆过滤器处理。",
        72,
        "覆盖缓存穿透，但没有说明更新一致性。",
        evaluation("缓存空值"),
        "FOLLOW_UP",
        "基础方案正确，需要继续验证一致性处理。"
    );
  }

  @Test
  void rejectsSwitchTopicThatTargetsFollowUp() {
    stubIncrementalSession();
    when(persistenceService.getAgentDecisions("session-1")).thenReturn(List.of());

    assertThrows(BusinessException.class, () -> service.applyAdaptiveTurn(
        "session-1",
        new AgentInterviewTurnCommand(
            0,
            "回答内容",
            "SWITCH_TOPIC",
            "准备切换到新的考察方向。",
            85,
            "当前问题已经回答充分。",
            "KEEP",
            nextIntent(true),
            evaluation("回答内容"),
            null,
            "ANSWER"
        )
    ));
  }

  @Test
  void rejectsEvaluationEvidenceThatIsNotInAnswer() {
    assertThrows(BusinessException.class, () -> service.applyAdaptiveTurn(
        "session-1",
        new AgentInterviewTurnCommand(
            0,
            "只回答了缓存空值。",
            "END_INTERVIEW",
            "当前回答信息不足，继续追问价值较低。",
            40,
            "回答缺少关键实现细节。",
            "KEEP",
            null,
            evaluation("布隆过滤器"),
            "LOW_INFORMATION",
            "ANSWER"
        )
    ));
  }

  @Test
  void recordsPartialCoverageWhenAgentEndsWithLowInformation() {
    stubSession();
    when(persistenceService.getAgentDecisions("session-1")).thenReturn(List.of());
    when(persistenceService.appendAgentDecision(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(persistenceService.getInterviewBlueprint("session-1")).thenReturn(
        new InterviewBlueprintDTO(
            "JOB_TARGETED",
            "验证岗位要求",
            List.of("REQ-1", "REQ-2"),
            List.of("Redis"),
            List.of("PROJECT_EVIDENCE"),
            List.of(),
            "mid",
            4,
            2,
            "验证证据"
        )
    );

    var result = service.applyAdaptiveTurn(
        "session-1",
        new AgentInterviewTurnCommand(
            0,
            "通过缓存空值处理。",
            "END_INTERVIEW",
            "连续回答信息有限，当前继续追问的判断价值较低。",
            45,
            "基础方向正确，但缺少具体实现和异常场景。",
            "KEEP",
            null,
            evaluation("缓存空值"),
            "LOW_INFORMATION",
            "ANSWER"
        )
    );

    assertTrue(result.completed());
    verify(persistenceService).completeSession(
        "session-1",
        EndReason.LOW_INFORMATION,
        CompletionType.PARTIAL,
        List.of("REQ-1", "Redis"),
        List.of("REQ-2")
    );
  }

  @Test
  void userEndIntentCompletesWithoutAnswerEvaluation() {
    stubSession();

    var result = service.applyAdaptiveTurn(
        "session-1",
        new AgentInterviewTurnCommand(
            0, null, "END_INTERVIEW", "用户主动结束面试。", 0,
            "本轮未评分", "KEEP", null, null, "USER_REQUESTED", "END")
    );

    assertTrue(result.completed());
    verify(persistenceService).saveControlTurn(
        "session-1", 0, "如何处理缓存穿透？", "Redis", "END", "用户主动结束面试。"
    );
    verify(persistenceService).completeSession(
        "session-1", EndReason.USER_REQUESTED, CompletionType.PARTIAL, List.of(), List.of()
    );
  }

  @Test
  void skipIntentMovesToNextMainQuestionWithoutScoring() {
    stubSession();

    var result = service.applyAdaptiveTurn(
        "session-1",
        new AgentInterviewTurnCommand(
            0, null, "SWITCH_TOPIC", "用户跳过当前问题。", 0,
            "本轮未评分", "KEEP", null, null, null, "SKIP")
    );

    assertFalse(result.completed());
    assertEquals(2, result.nextQuestion().questionIndex());
    assertEquals(null, result.decision());
    verify(persistenceService).updateCurrentQuestionIndex("session-1", 2);
    verify(persistenceService).saveControlTurn(
        "session-1", 0, "如何处理缓存穿透？", "Redis", "SKIP", "用户跳过当前问题。"
    );
  }

  private void stubSession() {
    List<InterviewQuestionDTO> questions = List.of(
        InterviewQuestionDTO.create(
            0, "如何处理缓存穿透？", "REDIS", "Redis", "缓存穿透", false, null, "REQ-1"),
        InterviewQuestionDTO.create(
            1, "缓存更新如何保证一致性？", "REDIS", "Redis · 追问1", null, true, 0, "REQ-1"),
        InterviewQuestionDTO.create(
            2, "解释 MySQL 索引失效场景。", "MYSQL", "MySQL", "索引失效", false, null, "REQ-2")
    );
    CachedSession session = new CachedSession(
        "session-1", "", 11L, questions, 0, SessionStatus.IN_PROGRESS, objectMapper);
    when(sessionCache.getSession("session-1")).thenReturn(Optional.of(session));
    when(persistenceService.findBySessionId("session-1")).thenReturn(Optional.of(sessionEntity(null)));
  }

  private void stubIncrementalSession() {
    List<InterviewQuestionDTO> questions = List.of(
        InterviewQuestionDTO.create(
            0, "如何处理缓存穿透？", "PROJECT_EVIDENCE", "Redis", "缓存穿透",
            false, null, "REQ-1")
    );
    CachedSession session = new CachedSession(
        "session-1", "", 11L, questions, 0, SessionStatus.IN_PROGRESS, objectMapper);
    when(sessionCache.getSession("session-1")).thenReturn(Optional.of(session));
    when(persistenceService.findBySessionId("session-1"))
        .thenReturn(Optional.of(sessionEntity("agent:create:1")));
    when(persistenceService.getInterviewBlueprint("session-1")).thenReturn(blueprint());
  }

  private InterviewSessionEntity sessionEntity(String agentCreationKey) {
    InterviewSessionEntity entity = new InterviewSessionEntity();
    entity.setSessionId("session-1");
    entity.setTotalQuestions(4);
    entity.setDifficulty("mid");
    entity.setSkillId("java-backend");
    entity.setLlmProvider("dashscope");
    entity.setResumeTextSnapshot("候选人有 Redis 项目经验");
    entity.setJdTextSnapshot("岗位要求熟悉 Redis");
    entity.setAgentCreationKey(agentCreationKey);
    return entity;
  }

  private InterviewBlueprintDTO blueprint() {
    return new InterviewBlueprintDTO(
        "JOB_TARGETED", "验证岗位要求", List.of("REQ-1", "REQ-2"), List.of("Redis"),
        List.of("PROJECT_EVIDENCE", "TROUBLESHOOTING"), List.of(), "mid", 4, 2,
        "验证能力证据");
  }

  private AgentNextQuestionIntent nextIntent(boolean followUp) {
    return new AgentNextQuestionIntent(
        "PROJECT_EVIDENCE",
        "Redis 一致性",
        "REQ-1",
        "mid",
        followUp,
        followUp ? 0 : null,
        "验证缓存更新与数据库写入的一致性处理能力"
    );
  }

  private AgentInterviewTurnEvaluation evaluation(String evidence) {
    return new AgentInterviewTurnEvaluation(
        true,
        75,
        65,
        70,
        60,
        null,
        62,
        72,
        78,
        70,
        74,
        List.of("缺少一致性说明"),
        List.of(),
        List.of(evidence),
        82
    );
  }
}
