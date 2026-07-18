package com.yzh666.careerai.modules.interview.service;

import com.yzh666.careerai.common.ai.LlmProviderRegistry;
import com.yzh666.careerai.common.ai.PromptSanitizer;
import com.yzh666.careerai.common.ai.PromptSecurityConstants;
import com.yzh666.careerai.common.ai.StructuredOutputInvoker;
import com.yzh666.careerai.common.agent.tool.AgentNextQuestionIntent;
import com.yzh666.careerai.common.constant.CommonConstants.InterviewDefaults;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.interview.model.HistoricalQuestion;
import com.yzh666.careerai.modules.interview.model.InterviewBlueprintDTO;
import com.yzh666.careerai.modules.interview.model.InterviewQuestionDTO;
import com.yzh666.careerai.modules.interview.skill.InterviewSkillService;
import com.yzh666.careerai.modules.interview.skill.InterviewSkillService.CategoryDTO;
import com.yzh666.careerai.modules.interview.skill.InterviewSkillService.SkillDTO;
import com.yzh666.careerai.modules.interview.skill.InterviewSkillService.SkillCategoryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面试问题生成服务
 * 根据 Agent 面试蓝图生成开场题，并按每轮受控意图增量生成下一题。
 */
@Service
public class InterviewQuestionService {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);

    private static final String DEFAULT_QUESTION_TYPE = "GENERAL";
    private static final int MAX_FOLLOW_UP_COUNT = 2;
    private static final int OPENING_QUESTION_CANDIDATE_COUNT = 3;
    private static final double TOPIC_REPEAT_SIMILARITY = 0.42;

    private static final String GENERIC_MODE_SYSTEM_APPEND = """
        \n\n# 通用面试模式
        本次面试无候选人简历，请出该方向的标准面试题。
        - 禁止出现"你在简历中提到..."、"你在项目中..."等暗示存在简历的表述
        - 问题表述应与简历无关，直接考察该方向的技术能力
        """;

    private static final Map<String, String> DIFFICULTY_DESCRIPTIONS = Map.of(
        "junior", "校招/0-1年经验。考察基础概念和简单应用。",
        "mid", "1-3年经验。考察原理理解和实战经验。",
        "senior", "3年+经验。考察架构设计和深度调优。"
    );

    private static final String[][] GENERIC_FALLBACK_QUESTIONS = {
        {"请描述一个你主导解决的技术难题，你的分析思路是什么？", "GENERAL", "综合能力"},
        {"你在做技术方案选型时，通常考虑哪些因素？请举例说明。", "GENERAL", "综合能力"},
        {"请分享一次你处理线上故障的经历，从发现到修复的完整过程。", "GENERAL", "综合能力"},
        {"你如何保证代码质量？介绍你实践过的有效手段。", "GENERAL", "综合能力"},
        {"描述一个你做过的技术优化案例，优化的动机、方案和效果。", "GENERAL", "综合能力"},
        {"你在团队协作中遇到过最大的分歧是什么？如何解决的？", "GENERAL", "综合能力"},
    };

    private final PromptTemplate skillSystemPromptTemplate;
    private final PromptTemplate skillUserPromptTemplate;
    private final PromptTemplate resumeSystemPromptTemplate;
    private final PromptTemplate resumeUserPromptTemplate;
    private final BeanOutputConverter<QuestionListDTO> outputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final InterviewSkillService skillService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final PromptSanitizer promptSanitizer;
    private final int followUpCount;

    private record QuestionListDTO(List<QuestionDTO> questions) {}

    private record QuestionDTO(String question, String type, String category,
                               String topicSummary, String requirementId, List<String> followUps) {}

    public InterviewQuestionService(
            StructuredOutputInvoker structuredOutputInvoker,
            InterviewSkillService skillService,
            InterviewQuestionProperties properties,
            ResourceLoader resourceLoader,
            LlmProviderRegistry llmProviderRegistry,
            PromptSanitizer promptSanitizer) throws IOException {
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.skillService = skillService;
        this.llmProviderRegistry = llmProviderRegistry;
        this.promptSanitizer = promptSanitizer;
        this.skillSystemPromptTemplate = loadTemplate(resourceLoader, properties.getQuestionSystemPromptPath());
        this.skillUserPromptTemplate = loadTemplate(resourceLoader, properties.getQuestionUserPromptPath());
        this.resumeSystemPromptTemplate = loadTemplate(resourceLoader, properties.getResumeQuestionSystemPromptPath());
        this.resumeUserPromptTemplate = loadTemplate(resourceLoader, properties.getResumeQuestionUserPromptPath());
        this.outputConverter = new BeanOutputConverter<>(QuestionListDTO.class);
        this.followUpCount = Math.max(0, Math.min(properties.getFollowUpCount(), MAX_FOLLOW_UP_COUNT));
    }

    private static PromptTemplate loadTemplate(ResourceLoader loader, String location) throws IOException {
        return new PromptTemplate(loader.getResource(location).getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * 为 Agent 会话生成首题候选，并在 Java 侧执行跨会话去重。
     *
     * <p>首题只生成一个候选时，模型即使看到了历史题目，也可能继续选择简历中最显眼的项目亮点。
     * 这里一次生成少量候选，再优先选择没有考过的主题；当候选全部属于历史主题时仍允许回退，
     * 以支持用户主动发起的专项复测。</p>
     */
    public InterviewQuestionDTO generateOpeningQuestion(
            String llmProvider,
            String skillId,
            String difficulty,
            String resumeText,
            List<HistoricalQuestion> historicalQuestions,
            List<CategoryDTO> customCategories,
            String jdText,
            String jobMatchContext,
            InterviewBlueprintDTO blueprint) {
        SkillDTO skill = resolveSkill(skillId, customCategories, jdText);
        String difficultyDesc = resolveDifficulty(difficulty);
        ChatClient questionChatClient = llmProviderRegistry.getPlainChatClient(llmProvider);
        String historicalSection = buildHistoricalSection(historicalQuestions);
        String blueprintSection = buildBlueprintSection(blueprint);

        // 首题候选不会直接保存追问，关闭追问生成可以减少无效 token 消耗。
        List<InterviewQuestionDTO> candidates = resumeText != null && !resumeText.isBlank()
            ? generateResumeQuestions(
                questionChatClient,
                resumeText,
                OPENING_QUESTION_CANDIDATE_COUNT,
                skill,
                difficultyDesc,
                historicalSection,
                jobMatchContext,
                blueprintSection,
                0)
            : generateDirectionOnly(
                questionChatClient,
                skill,
                difficultyDesc,
                OPENING_QUESTION_CANDIDATE_COUNT,
                historicalSection,
                jobMatchContext,
                blueprintSection,
                0);
        return selectFirstNovelMainQuestion(candidates, historicalQuestions);
    }

    static InterviewQuestionDTO selectFirstNovelMainQuestion(
            List<InterviewQuestionDTO> candidates,
            List<HistoricalQuestion> historicalQuestions) {
        List<InterviewQuestionDTO> mainQuestions = candidates == null ? List.of() : candidates.stream()
            .filter(question -> question != null && !question.isFollowUp())
            .toList();
        if (mainQuestions.isEmpty()) {
            throw new BusinessException(
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "首题生成失败");
        }

        InterviewQuestionDTO selected = mainQuestions.stream()
            .filter(question -> !isHistoricalRepeat(question, historicalQuestions))
            .findFirst()
            .orElse(mainQuestions.getFirst());
        if (selected != mainQuestions.getFirst()) {
            log.info("首题候选去重生效: 跳过主题={}, 选中主题={}",
                displayTopic(mainQuestions.getFirst()), displayTopic(selected));
        } else if (isHistoricalRepeat(selected, historicalQuestions)) {
            log.info("首题候选均为历史主题，使用最先生成的候选避免会话创建失败: topic={}",
                displayTopic(selected));
        }
        return selected;
    }

    private static boolean isHistoricalRepeat(
            InterviewQuestionDTO candidate,
            List<HistoricalQuestion> historicalQuestions) {
        if (historicalQuestions == null || historicalQuestions.isEmpty()) {
            return false;
        }
        String candidateQuestion = normalizeForComparison(candidate.question());
        String candidateTopic = normalizeForComparison(candidate.topicSummary());
        return historicalQuestions.stream().anyMatch(history -> {
            String historicalQuestion = normalizeForComparison(history.question());
            if (!candidateQuestion.isEmpty() && candidateQuestion.equals(historicalQuestion)) {
                return true;
            }
            String historicalTopic = normalizeForComparison(history.topicSummary());
            return !candidateTopic.isEmpty()
                && !historicalTopic.isEmpty()
                && bigramDice(candidateTopic, historicalTopic) >= TOPIC_REPEAT_SIMILARITY;
        });
    }

    private static double bigramDice(String left, String right) {
        if (left.equals(right)) {
            return 1.0;
        }
        Set<String> leftBigrams = bigrams(left);
        Set<String> rightBigrams = bigrams(right);
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return 0.0;
        }
        long intersection = leftBigrams.stream().filter(rightBigrams::contains).count();
        return (2.0 * intersection) / (leftBigrams.size() + rightBigrams.size());
    }

    private static Set<String> bigrams(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < value.length() - 1; index++) {
            result.add(value.substring(index, index + 2));
        }
        return result;
    }

    private static String normalizeForComparison(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        value.toLowerCase().codePoints()
            .filter(Character::isLetterOrDigit)
            .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }

    private static String displayTopic(InterviewQuestionDTO question) {
        return question.topicSummary() == null || question.topicSummary().isBlank()
            ? question.question()
            : question.topicSummary();
    }

    /**
     * 根据 Agent 的受控意图生成唯一下一题。Python 负责决策“考什么”，
     * Java 负责注入简历、JD、历史题目并生成最终可执行问题。
     */
    public InterviewQuestionDTO generateNextQuestion(
            String llmProvider,
            String skillId,
            String resumeText,
            List<CategoryDTO> customCategories,
            String jdText,
            String jobMatchContext,
            InterviewBlueprintDTO blueprint,
            AgentNextQuestionIntent intent,
            InterviewQuestionDTO currentQuestion,
            String currentAnswer,
            List<InterviewQuestionDTO> askedQuestions) {
        SkillDTO skill = resolveSkill(skillId, customCategories, jdText);
        ChatClient questionChatClient = llmProviderRegistry.getPlainChatClient(llmProvider);
        List<HistoricalQuestion> history = askedQuestions == null ? List.of() : askedQuestions.stream()
            .filter(question -> !question.isFollowUp())
            .map(question -> new HistoricalQuestion(
                question.question(), question.type(), question.topicSummary()))
            .toList();
        String controlledContext = buildControlledIntentContext(
            jobMatchContext, intent, currentQuestion, currentAnswer);
        String difficultyDesc = resolveDifficulty(intent.difficulty());
        int effectiveFollowUpCount = blueprint == null
            ? followUpCount : Math.min(followUpCount, blueprint.maxFollowUpsPerTopic());
        String historySection = buildHistoricalSection(history);
        String blueprintSection = buildBlueprintSection(blueprint);

        List<InterviewQuestionDTO> generated = resumeText != null && !resumeText.isBlank()
            ? generateResumeQuestions(
                questionChatClient,
                resumeText,
                1,
                skill,
                difficultyDesc,
                historySection,
                controlledContext,
                blueprintSection,
                effectiveFollowUpCount)
            : generateDirectionOnly(
                questionChatClient,
                skill,
                difficultyDesc,
                1,
                historySection,
                controlledContext,
                blueprintSection,
                effectiveFollowUpCount);
        InterviewQuestionDTO selected = generated.stream()
            .filter(question -> question.isFollowUp() == intent.followUp())
            .findFirst()
            .orElseGet(() -> generated.stream().findFirst()
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                    "增量生成下一题失败")));
        int nextIndex = askedQuestions == null ? 0 : askedQuestions.size();
        return InterviewQuestionDTO.create(
            nextIndex,
            selected.question(),
            intent.questionType().toUpperCase(),
            intent.topic(),
            intent.topic(),
            intent.followUp(),
            intent.followUp() ? intent.parentQuestionIndex() : null,
            intent.requirementId());
    }

    private String buildControlledIntentContext(
            String jobMatchContext,
            AgentNextQuestionIntent intent,
            InterviewQuestionDTO currentQuestion,
            String currentAnswer) {
        String base = jobMatchContext == null ? "" : jobMatchContext;
        return base + """

            # 本轮增量出题意图
            - 题型：%s
            - 主题：%s
            - 关联要求：%s
            - 难度：%s
            - 是否追问：%s
            - 考察目标：%s
            - 当前问题：%s
            - 候选人回答：%s

            必须严格围绕本轮意图生成一道题，不要重复已考内容。
            """.formatted(
                intent.questionType(),
                intent.topic(),
                displayOrNone(intent.requirementId()),
                intent.difficulty(),
                intent.followUp() ? "是" : "否",
                intent.objective(),
                currentQuestion == null ? "无" : currentQuestion.question(),
                displayOrNone(currentAnswer));
    }

    private List<InterviewQuestionDTO> generateResumeQuestions(
            ChatClient questionClient, String resumeText, int questionCount,
            SkillDTO skill, String difficultyDesc, String historicalSection, String jobMatchContext,
            String blueprintSection, int effectiveFollowUpCount) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("followUpCount", effectiveFollowUpCount);
            variables.put("skillName", skill.name());
            variables.put("skillDescription", skill.description() != null ? skill.description() : "");
            variables.put("difficultyDescription", difficultyDesc);
            variables.put("resumeText", resumeText);
            variables.put("historicalSection", historicalSection);
            variables.put("jobMatchSection", buildJobMatchSection(jobMatchContext));
            variables.put("blueprintSection", blueprintSection);

            String systemPrompt = resumeSystemPromptTemplate.render()
                + buildSkillPersonaSection(skill)
                + "\n\n" + outputConverter.getFormat();
            String userPrompt = resumeUserPromptTemplate.render(variables);

            QuestionListDTO dto = structuredOutputInvoker.invoke(
                questionClient, systemPrompt, userPrompt, outputConverter,
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "简历题生成失败：", "简历题", log);

            List<InterviewQuestionDTO> questions = convertToQuestions(dto, effectiveFollowUpCount);
            questions = capToMainCount(questions, questionCount);
            log.info("简历题生成完成: 请求={}, 实际主问题={}",
                questionCount, questions.stream().filter(q -> !q.isFollowUp()).count());
            return questions;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("简历题生成异常: {}", e.getMessage(), e);
            throw e;
        }
    }

    private List<InterviewQuestionDTO> generateDirectionOnly(
            ChatClient questionClient, SkillDTO skill, String difficultyDesc,
            int questionCount, String historicalSection, String jobMatchContext,
            String blueprintSection, int effectiveFollowUpCount) {
        Map<String, Integer> allocation = skillService.calculateAllocation(skill.categories(), questionCount);
        String allocationTable = skillService.buildAllocationDescription(allocation, skill.categories());

        log.info("方向题生成: skill={}, total={}, allocation={}",
            skill.id(), questionCount, allocation);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("followUpCount", effectiveFollowUpCount);
            variables.put("difficultyDescription", difficultyDesc);
            variables.put("skillName", skill.name());
            variables.put("skillDescription", skill.description() != null ? skill.description() : "");
            variables.put("allocationTable", allocationTable);
            variables.put("historicalSection", historicalSection);
            variables.put("referenceSection", skillService.buildReferenceSection(skill, allocation));
            variables.put("jdSection", buildJdSection(skill.sourceJd()));
            variables.put("jobMatchSection", buildJobMatchSection(jobMatchContext));
            variables.put("blueprintSection", blueprintSection);

            String systemPrompt = skillSystemPromptTemplate.render()
                + buildSkillPersonaSection(skill)
                + GENERIC_MODE_SYSTEM_APPEND
                + outputConverter.getFormat();
            String userPrompt = skillUserPromptTemplate.render(variables);

            QuestionListDTO dto = structuredOutputInvoker.invoke(
                questionClient, systemPrompt, userPrompt, outputConverter,
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "方向题生成失败：", "方向题", log);

            List<InterviewQuestionDTO> questions = convertToQuestions(dto, effectiveFollowUpCount);
            if (questions.stream().filter(q -> !q.isFollowUp()).count() == 0) {
                log.warn("方向题返回空题单，回退到默认问题");
                return generateFallbackQuestions(skill, questionCount, effectiveFollowUpCount);
            }
            questions = capToMainCount(questions, questionCount);
            log.info("方向题生成完成: 请求={}, 实际主问题={}",
                questionCount, questions.stream().filter(q -> !q.isFollowUp()).count());
            return questions;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("方向题生成失败，回退到默认问题: {}", e.getMessage(), e);
            return generateFallbackQuestions(skill, questionCount, effectiveFollowUpCount);
        }
    }

    private SkillDTO resolveSkill(String skillId, List<CategoryDTO> customCategories, String jdText) {
        if (InterviewSkillService.CUSTOM_SKILL_ID.equals(skillId)
                && customCategories != null && !customCategories.isEmpty()) {
            return skillService.buildCustomSkill(customCategories, jdText != null ? jdText : "");
        }
        return skillService.getSkill(skillId);
    }

    private String resolveDifficulty(String difficulty) {
        return DIFFICULTY_DESCRIPTIONS.getOrDefault(
            difficulty != null ? difficulty : InterviewDefaults.DIFFICULTY,
            DIFFICULTY_DESCRIPTIONS.get(InterviewDefaults.DIFFICULTY));
    }

    private List<InterviewQuestionDTO> convertToQuestions(QuestionListDTO dto, int effectiveFollowUpCount) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (dto == null || dto.questions() == null) {
            return questions;
        }

        for (QuestionDTO q : dto.questions()) {
            if (q == null || q.question() == null || q.question().isBlank()) {
                continue;
            }
            String type = (q.type() != null && !q.type().isBlank()) ? q.type().toUpperCase() : DEFAULT_QUESTION_TYPE;
            int mainQuestionIndex = index;
            questions.add(InterviewQuestionDTO.create(
                index++, q.question(), type, q.category(), q.topicSummary(), false, null,
                normalizeRequirementId(q.requirementId())));

            List<String> followUps = sanitizeFollowUps(q.followUps(), effectiveFollowUpCount);
            for (int i = 0; i < followUps.size(); i++) {
                questions.add(InterviewQuestionDTO.create(
                    index++, followUps.get(i), type,
                    buildFollowUpCategory(q.category(), i + 1), null, true, mainQuestionIndex,
                    normalizeRequirementId(q.requirementId())
                ));
            }
        }

        return questions;
    }

    private String normalizeRequirementId(String requirementId) {
        if (requirementId == null || requirementId.isBlank()) {
            return null;
        }
        String normalized = requirementId.trim().toUpperCase();
        return normalized.matches("REQ-[A-Z0-9_-]{1,24}") ? normalized : null;
    }

    /**
     * 将问题列表截断到指定的主问题数量（AI 多生时截断，少生时保留原样并记录警告）。
     */
    private List<InterviewQuestionDTO> capToMainCount(
            List<InterviewQuestionDTO> questions, int maxMainCount) {
        long currentMainCount = questions.stream().filter(q -> !q.isFollowUp()).count();

        if (currentMainCount <= maxMainCount) {
            if (currentMainCount < maxMainCount) {
                log.warn("AI 生成主问题不足: 请求={}, 实际={}", maxMainCount, currentMainCount);
            }
            return questions;
        }

        List<InterviewQuestionDTO> capped = new ArrayList<>();
        int mainSeen = 0;
        for (InterviewQuestionDTO q : questions) {
            if (!q.isFollowUp()) {
                mainSeen++;
            }
            if (mainSeen > maxMainCount) {
                break;
            }
            capped.add(q);
        }
        log.info("题目截断: 主问题 {} → {}", currentMainCount, maxMainCount);
        return capped;
    }

    private List<InterviewQuestionDTO> generateFallbackQuestions(
            SkillDTO skill, int count, int effectiveFollowUpCount) {
        List<SkillCategoryDTO> categories = skill != null ? skill.categories() : List.of();
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (!categories.isEmpty()) {
            int generated = 0;
            while (generated < count) {
                SkillCategoryDTO cat = categories.get(generated % categories.size());
                String question = "请谈谈你在\"" + cat.label() + "\"方向的技术理解和实践经验。";
                questions.add(InterviewQuestionDTO.create(index++, question, cat.key(), cat.label(), null, false, null));
                int mainIndex = index - 1;
                for (int j = 0; j < effectiveFollowUpCount; j++) {
                    questions.add(InterviewQuestionDTO.create(
                        index++, buildDefaultFollowUp(question, j + 1),
                        cat.key(), buildFollowUpCategory(cat.label(), j + 1), null, true, mainIndex
                    ));
                }
                generated++;
            }
            return questions;
        }

        for (int i = 0; i < Math.min(count, GENERIC_FALLBACK_QUESTIONS.length); i++) {
            String[] q = GENERIC_FALLBACK_QUESTIONS[i];
            questions.add(InterviewQuestionDTO.create(index++, q[0], q[1], q[2], null, false, null));
            int mainIndex = index - 1;
            for (int j = 0; j < effectiveFollowUpCount; j++) {
                questions.add(InterviewQuestionDTO.create(
                    index++, buildDefaultFollowUp(q[0], j + 1),
                    q[1], buildFollowUpCategory(q[2], j + 1), null, true, mainIndex
                ));
            }
        }
        return questions;
    }

    private String buildHistoricalSection(List<HistoricalQuestion> historicalQuestions) {
        if (historicalQuestions == null || historicalQuestions.isEmpty()) {
            return "暂无历史提问";
        }

        Map<String, List<String>> grouped = new HashMap<>();
        for (HistoricalQuestion hq : historicalQuestions) {
            String type = hq.type() != null && !hq.type().isBlank() ? hq.type() : DEFAULT_QUESTION_TYPE;
            String summary = hq.topicSummary();
            if (summary == null || summary.isBlank()) {
                String q = hq.question();
                summary = q.length() > 30 ? q.substring(0, 30) + "…" : q;
            }
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(summary);
        }

        StringBuilder sb = new StringBuilder("已考过的知识点（避免重复出题）：\n");
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ");
            sb.append(String.join(", ", entry.getValue()));
            sb.append('\n');
        }
        return sb.toString();
    }

    private String buildJdSection(String sourceJd) {
        if (sourceJd == null || sourceJd.isBlank()) {
            return "";
        }
        return PromptSecurityConstants.DATA_BOUNDARY_INSTRUCTION + "\n" +
            "## 职位描述（JD）\n根据以下 JD 关键要求出题，确保题目与岗位实际需求相关：\n" +
            promptSanitizer.wrapWithDelimiters("jd", promptSanitizer.sanitize(sourceJd));
    }

    private String buildJobMatchSection(String jobMatchContext) {
        if (jobMatchContext == null || jobMatchContext.isBlank()) {
            return "暂无简历-岗位匹配报告。";
        }
        return PromptSecurityConstants.DATA_BOUNDARY_INSTRUCTION + "\n" +
            "## 简历-岗位匹配报告\n" +
            "请优先围绕报告中的主要差距和行动项出题，但不要直接泄露报告原文给候选人：\n" +
            promptSanitizer.wrapWithDelimiters("job_match_report", promptSanitizer.sanitize(jobMatchContext));
    }

    private String buildBlueprintSection(InterviewBlueprintDTO blueprint) {
        if (blueprint == null) {
            return "暂无专项蓝图，请按默认面试方向均衡出题。";
        }
        return """
            ## Agent 面试生成蓝图
            - 训练模式：%s
            - 本轮目标：%s
            - 重点岗位要求：%s
            - 强化主题：%s
            - 题型组合：%s
            - 回避主题：%s
            - 目标难度：%s
            - 单主题最多追问：%d
            - 规划理由：%s

            蓝图描述的是本轮覆盖重点，不得据此编造简历或 JD 中不存在的经历。
            """.formatted(
                blueprint.mode(),
                displayOrNone(blueprint.objective()),
                displayList(blueprint.targetRequirementIds()),
                displayList(blueprint.focusTopics()),
                displayList(blueprint.questionTypes()),
                displayList(blueprint.avoidTopics()),
                blueprint.difficulty(),
                blueprint.maxFollowUpsPerTopic(),
                displayOrNone(blueprint.rationale()));
    }

    private String displayList(List<String> values) {
        return values == null || values.isEmpty() ? "无" : String.join("、", values);
    }

    private String displayOrNone(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private String buildSkillPersonaSection(SkillDTO skill) {
        if (skill == null || skill.persona() == null || skill.persona().isBlank()) {
            return "";
        }
        return "\n\n# Skill Persona\n"
            + "以下内容来自当前面试方向的 SKILL.md，请作为面试官角色、风格与出题约束：\n"
            + promptSanitizer.wrapWithDelimiters("skill_persona", skill.persona());
    }

    private List<String> sanitizeFollowUps(List<String> followUps, int effectiveFollowUpCount) {
        if (effectiveFollowUpCount == 0 || followUps == null || followUps.isEmpty()) {
            return List.of();
        }
        return followUps.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .limit(effectiveFollowUpCount)
            .collect(Collectors.toList());
    }

    private String buildFollowUpCategory(String category, int order) {
        String base = (category == null || category.isBlank()) ? "追问" : category;
        return base + "（追问" + order + "）";
    }

    private String buildDefaultFollowUp(String mainQuestion, int order) {
        if (order == 1) {
            return "基于\"" + mainQuestion + "\"，请结合你亲自做过的一个真实场景展开说明。";
        }
        return "基于\"" + mainQuestion + "\"，如果线上出现异常，你会如何定位并给出修复方案？";
    }
}
