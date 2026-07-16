package com.yzh666.careerai.modules.interview.model;

import java.util.List;

/**
 * 面试评估报告
 */
public record InterviewReportDTO(
    String sessionId,
    int totalQuestions,
    int overallScore,                          // 总分 (0-100)
    List<CategoryScore> categoryScores,        // 各类别得分
    List<QuestionEvaluation> questionDetails,  // 每题详情
    String overallFeedback,                    // 总体评价
    List<String> strengths,                    // 优势
    List<String> improvements,                 // 改进建议
    JobEvaluation jobEvaluation,               // 岗位化评价
    List<ReferenceAnswer> referenceAnswers,    // 参考答案
    String endReason,
    String completionType,
    List<String> coveredTargets,
    List<String> unverifiedTargets,
    List<DimensionScore> dimensionScores,
    List<EvidenceConclusion> evidenceConclusions
) {
    /**
     * 类别得分
     */
    public record CategoryScore(
        String category,
        int score,
        int questionCount
    ) {}
    
    /**
     * 问题评估详情
     */
    public record QuestionEvaluation(
        int questionIndex,
        String question,
        String category,
        String userAnswer,
        int score,
        String feedback
    ) {}
    
    /**
     * 参考答案
     */
    public record ReferenceAnswer(
        int questionIndex,
        String question,
        String referenceAnswer,
        List<String> keyPoints
    ) {}

    /**
     * 岗位化评价
     */
    public record JobEvaluation(
        String targetJobTitle,
        String conclusion,
        int jdCoverageScore,
        List<String> jdCoverage,
        List<String> exposedGaps,
        List<String> resumeRewriteSuggestions,
        List<String> nextActions
    ) {}

    /** 多个有效轮次在同一能力维度上的平均观察。 */
    public record DimensionScore(
        String dimension,
        int score,
        int evidenceCount
    ) {}

    /** 可追溯到具体问题和回答原文片段的评价结论。 */
    public record EvidenceConclusion(
        int questionIndex,
        String question,
        String dimension,
        int score,
        String feedback,
        List<String> evidenceSnippets,
        List<String> missingPoints,
        int confidence
    ) {}
}
