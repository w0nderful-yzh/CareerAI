package com.yzh666.careerai.modules.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.yzh666.careerai.modules.interview.model.HistoricalQuestion;
import com.yzh666.careerai.modules.interview.model.InterviewQuestionDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewQuestionServiceNoveltyTest {

  @Test
  void skipsOpeningCandidateWithSemanticallyRepeatedTopic() {
    List<HistoricalQuestion> history = List.of(new HistoricalQuestion(
        "请解释 Lua 脚本如何保证库存校验的原子性？",
        "REDIS",
        "Lua 原子操作与 Redis"
    ));
    List<InterviewQuestionDTO> candidates = List.of(
        question(0, "Lua 脚本的参数和返回值如何设计？", "Lua 原子校验"),
        question(1, "Redis ZSet 的滚动分页如何避免重复数据？", "Redis ZSet 滚动分页设计"),
        question(2, "如何定位缓存一致性问题？", "缓存一致性排查")
    );

    InterviewQuestionDTO selected = InterviewQuestionService.selectFirstNovelMainQuestion(
        candidates, history);

    assertEquals("Redis ZSet 滚动分页设计", selected.topicSummary());
  }

  @Test
  void keepsFirstCandidateWhenAllCandidatesAreRetests() {
    List<HistoricalQuestion> history = List.of(new HistoricalQuestion(
        "请解释 Lua 脚本如何保证库存校验的原子性？",
        "REDIS",
        "Lua 原子操作与 Redis"
    ));
    List<InterviewQuestionDTO> candidates = List.of(
        question(0, "Lua 脚本的参数和返回值如何设计？", "Lua 原子校验"),
        question(1, "Lua 原子扣减失败时如何返回错误？", "Lua 原子扣减")
    );

    InterviewQuestionDTO selected = InterviewQuestionService.selectFirstNovelMainQuestion(
        candidates, history);

    assertEquals("Lua 原子校验", selected.topicSummary());
  }

  private InterviewQuestionDTO question(int index, String text, String topic) {
    return InterviewQuestionDTO.create(index, text, "REDIS", "项目经历", topic, false, null);
  }
}
