package com.yzh666.careerai.modules.agenttool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.common.agent.tool.AgentJobMatchCommand;
import com.yzh666.careerai.common.agent.tool.AgentResumeImprovementPlanCommand;
import com.yzh666.careerai.common.model.AsyncTaskStatus;
import com.yzh666.careerai.modules.job.service.JobService;
import com.yzh666.careerai.modules.jobmatch.dto.JdRequirementDTO;
import com.yzh666.careerai.modules.jobmatch.dto.CreateJobMatchRequest;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchReportDTO;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchTaskDTO;
import com.yzh666.careerai.modules.jobmatch.dto.RequirementEvidenceDTO;
import com.yzh666.careerai.modules.jobmatch.dto.ResumeEvidenceDTO;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchService;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchTaskService;
import com.yzh666.careerai.modules.resume.service.ResumeHistoryService;
import com.yzh666.careerai.modules.resumeplan.service.ResumeImprovementPlanService;
import com.yzh666.careerai.modules.resumeplan.dto.ResumeImprovementPlanDTO;
import com.yzh666.careerai.modules.interview.service.AbilityProfileService;
import com.yzh666.careerai.modules.interview.service.InterviewPersistenceService;
import com.yzh666.careerai.modules.interview.service.InterviewSessionService;
import com.yzh666.careerai.modules.interview.service.InterviewClosureService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentBusinessToolServiceTest {

  @Mock
  private ResumeHistoryService resumeHistoryService;

  @Mock
  private JobService jobService;

  @Mock
  private JobMatchTaskService jobMatchTaskService;

  @Mock
  private JobMatchService jobMatchService;

  @Mock
  private ResumeImprovementPlanService improvementPlanService;

  @Mock
  private InterviewSessionService interviewSessionService;

  @Mock
  private InterviewPersistenceService interviewPersistenceService;

  @Mock
  private AbilityProfileService abilityProfileService;

  @Mock
  private InterviewClosureService interviewClosureService;

  @InjectMocks
  private AgentBusinessToolService service;

  @Test
  void startsJobMatchWithDomainServiceAndIdempotencyKey() {
    LocalDateTime now = LocalDateTime.now();
    JobMatchTaskDTO task = new JobMatchTaskDTO(
        91L,
        AsyncTaskStatus.PENDING,
        11L,
        22L,
        null,
        0,
        null,
        null,
        now,
        now
    );
    when(jobMatchTaskService.createTaskIdempotently(
        new CreateJobMatchRequest(11L, 22L),
        "run-1:match"
    )).thenReturn(task);

    var result = service.startJobMatch(
        new AgentJobMatchCommand(11L, 22L),
        "run-1:match"
    );

    assertEquals(91L, result.id());
    assertEquals("PENDING", result.status());
    verify(jobMatchTaskService).createTaskIdempotently(
        new CreateJobMatchRequest(11L, 22L),
        "run-1:match"
    );
  }

  @Test
  void forwardsAgentDecisionWhenCreatingImprovementPlan() {
    AgentResumeImprovementPlanCommand command = new AgentResumeImprovementPlanCommand(
        31L,
        "PROJECT_FIRST",
        "项目证据不足，应先补充可验证成果。",
        List.of("缺少高并发项目证据"),
        List.of("项目支撑得分低于技能得分"),
        List.of("项目架构取舍")
    );
    ResumeImprovementPlanDTO plan = new ResumeImprovementPlanDTO(
        41L,
        31L,
        11L,
        "resume.pdf",
        22L,
        "Java 后端工程师",
        82,
        "优先补强项目证据",
        List.of("补充量化指标"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        LocalDateTime.now()
    );
    when(improvementPlanService.createPlanIdempotently(command, "run-1:plan"))
        .thenReturn(plan);

    var result = service.createImprovementPlan(command, "run-1:plan");

    assertEquals(41L, result.id());
    verify(improvementPlanService).createPlanIdempotently(command, "run-1:plan");
  }

  @Test
  void exposesEvidenceMatrixToPythonAgent() {
    RequirementEvidenceDTO evidence = new RequirementEvidenceDTO(
        new JdRequirementDTO("REQ-1", "CACHE", "具备 Redis 缓存实践", "HIGH", "熟悉 Redis"),
        List.of(new ResumeEvidenceDTO("PROJECT", "CareerAI 项目", "使用 Redis 缓存", "WEAK")),
        "EVIDENCE_GAP",
        88,
        "只出现技术名称，缺少规模与结果",
        "补充缓存策略和效果指标"
    );
    JobMatchReportDTO report = new JobMatchReportDTO(
        31L,
        11L,
        "resume.pdf",
        22L,
        "Java 后端工程师",
        80,
        82,
        76,
        81,
        "总体匹配",
        List.of("Java 技术栈匹配"),
        List.of("Redis 证据不足"),
        List.of("补充缓存指标"),
        List.of(evidence),
        LocalDateTime.now()
    );
    when(jobMatchService.getReport(31L)).thenReturn(report);

    var result = service.getMatchReport(31L);

    assertEquals("REQ-1", result.evidenceMappings().getFirst().requirement().id());
    assertEquals("EVIDENCE_GAP", result.evidenceMappings().getFirst().coverageType());
    assertEquals("使用 Redis 缓存", result.evidenceMappings().getFirst().resumeEvidence().getFirst().quote());
  }
}
