package com.yzh666.careerai.modules.agenttool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.common.agent.tool.AgentJobMatchCommand;
import com.yzh666.careerai.common.model.AsyncTaskStatus;
import com.yzh666.careerai.modules.job.service.JobService;
import com.yzh666.careerai.modules.jobmatch.dto.CreateJobMatchRequest;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchTaskDTO;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchService;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchTaskService;
import com.yzh666.careerai.modules.resume.service.ResumeHistoryService;
import com.yzh666.careerai.modules.resumeplan.service.ResumeImprovementPlanService;
import java.time.LocalDateTime;
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
}
