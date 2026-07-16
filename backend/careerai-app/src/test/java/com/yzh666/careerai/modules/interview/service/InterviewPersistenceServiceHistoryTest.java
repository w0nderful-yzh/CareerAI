package com.yzh666.careerai.modules.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.modules.interview.model.InterviewQuestionDTO;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity;
import com.yzh666.careerai.modules.interview.repository.InterviewAnswerRepository;
import com.yzh666.careerai.modules.interview.repository.InterviewQuestionRecordRepository;
import com.yzh666.careerai.modules.interview.repository.InterviewSessionRepository;
import com.yzh666.careerai.modules.resume.repository.ResumeRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class InterviewPersistenceServiceHistoryTest {

  @Mock
  private InterviewSessionRepository sessionRepository;
  @Mock
  private InterviewAnswerRepository answerRepository;
  @Mock
  private InterviewQuestionRecordRepository questionRecordRepository;
  @Mock
  private ResumeRepository resumeRepository;
  @Mock
  private CurrentUserService currentUserService;

  private ObjectMapper objectMapper;
  private InterviewPersistenceService service;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    service = new InterviewPersistenceService(
        sessionRepository,
        answerRepository,
        questionRecordRepository,
        resumeRepository,
        objectMapper,
        currentUserService
    );
  }

  @Test
  void loadsHistoryAcrossResumeVersionsForCurrentUserAndSkill() throws Exception {
    when(currentUserService.currentUserId()).thenReturn(7L);
    when(sessionRepository.findTop10ByUserIdAndSkillIdOrderByCreatedAtDesc(7L, "java-backend"))
        .thenReturn(List.of(
            session("Lua 原子校验"),
            session("Redis ZSet 滚动分页设计")
        ));

    var history = service.getHistoricalQuestions("java-backend", 4L);

    assertEquals(List.of("Lua 原子校验", "Redis ZSet 滚动分页设计"),
        history.stream().map(item -> item.topicSummary()).toList());
    verify(sessionRepository)
        .findTop10ByUserIdAndSkillIdOrderByCreatedAtDesc(7L, "java-backend");
  }

  private InterviewSessionEntity session(String topic) throws Exception {
    InterviewSessionEntity entity = new InterviewSessionEntity();
    entity.setQuestionsJson(objectMapper.writeValueAsString(List.of(
        InterviewQuestionDTO.create(
            0, "关于" + topic + "的问题", "REDIS", "项目经历", topic, false, null)
    )));
    return entity;
  }
}
