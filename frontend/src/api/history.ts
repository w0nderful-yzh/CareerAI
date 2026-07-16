import { request } from './request';

export type AnalyzeStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type EvaluateStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface ResumeListItem {
  id: number;
  filename: string;
  fileSize: number;
  uploadedAt: string;
  accessCount: number;
  latestScore?: number;
  lastAnalyzedAt?: string;
  interviewCount: number;
  analyzeStatus?: AnalyzeStatus;
  analyzeError?: string;
  storageUrl?: string;
}

export interface ResumeStats {
  totalCount: number;
  totalInterviewCount: number;
  totalAccessCount: number;
}

export interface AnalysisItem {
  id: number;
  overallScore: number;
  contentScore: number;
  structureScore: number;
  skillMatchScore: number;
  expressionScore: number;
  projectScore: number;
  summary: string;
  analyzedAt: string;
  strengths: string[];
  suggestions: unknown[];
}

export interface InterviewItem {
  id: number;
  sessionId: string;
  jobId?: number | null;
  matchReportId?: number | null;
  totalQuestions: number;
  status: string;
  evaluateStatus?: EvaluateStatus;
  evaluateError?: string;
  overallScore: number | null;
  overallFeedback: string | null;
  createdAt: string;
  completedAt: string | null;
  questions?: unknown[];
  strengths?: string[];
  improvements?: string[];
  referenceAnswers?: unknown[];
}

export interface JobEvaluation {
  targetJobTitle: string;
  conclusion: string;
  jdCoverageScore: number;
  jdCoverage: string[];
  exposedGaps: string[];
  resumeRewriteSuggestions: string[];
  nextActions: string[];
}

export interface AnswerItem {
  questionIndex: number;
  question: string;
  category: string;
  userAnswer: string;
  score: number | null;
  feedback: string;
  referenceAnswer?: string;
  keyPoints?: string[];
  evaluation?: InterviewTurnEvaluation | null;
  agentAction?: string | null;
  decisionRationale?: string | null;
  answeredAt: string;
}

export interface InterviewTurnEvaluation {
  answered: boolean;
  technicalCorrectness?: number | null;
  technicalDepth?: number | null;
  completeness?: number | null;
  scenarioReasoning?: number | null;
  projectUnderstanding?: number | null;
  troubleshooting?: number | null;
  expressionStructure?: number | null;
  clarity?: number | null;
  credibility?: number | null;
  jobRelevance?: number | null;
  missingPoints: string[];
  errors: string[];
  evidenceSnippets: string[];
  confidence: number;
}

export interface ResumeDetail {
  id: number;
  filename: string;
  fileSize: number;
  contentType: string;
  storageUrl: string;
  uploadedAt: string;
  accessCount: number;
  resumeText: string;
  analyzeStatus?: AnalyzeStatus;
  analyzeError?: string;
  analyses: AnalysisItem[];
  interviews: InterviewItem[];
}

export interface InterviewDetail extends InterviewItem {
  evaluateStatus?: EvaluateStatus;
  evaluateError?: string;
  jobEvaluation?: JobEvaluation | null;
  endReason?: string | null;
  completionType?: 'COMPLETE' | 'PARTIAL' | null;
  coveredTargets?: string[];
  unverifiedTargets?: string[];
  answers: AnswerItem[];
}

export interface InterviewClosureEvidence {
  questionIndex: number;
  question: string;
  category: string;
  observedScore: number;
  evidenceSnippets: string[];
  missingPoints: string[];
  errors: string[];
}

export interface InterviewImprovementTask {
  id: number;
  idempotencyKey: string;
  questionIndex: number;
  category: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  status: 'TODO' | 'DONE' | string;
  title: string;
  rationale: string;
  verificationMethod: string;
  evidenceSnippets: string[];
}

export interface InterviewClosure {
  sessionId: string;
  completionType: 'COMPLETE' | 'PARTIAL';
  endReason: string;
  overallScore: number | null;
  summary: string;
  strengths: string[];
  observedWeaknesses: string[];
  coveredTargets: string[];
  unverifiedTargets: string[];
  keyEvidence: InterviewClosureEvidence[];
  nextInterviewSuggestions: string[];
  improvementTasks: InterviewImprovementTask[];
  generatedAt: string;
}

export const historyApi = {
  /**
   * 获取所有简历列表
   */
  async getResumes(): Promise<ResumeListItem[]> {
    return request.get<ResumeListItem[]>('/api/resumes');
  },

  /**
   * 获取简历详情
   */
  async getResumeDetail(id: number): Promise<ResumeDetail> {
    return request.get<ResumeDetail>(`/api/resumes/${id}/detail`);
  },

  /**
   * 获取面试详情
   */
  async getInterviewDetail(sessionId: string): Promise<InterviewDetail> {
    return request.get<InterviewDetail>(`/api/interview/sessions/${sessionId}/details`);
  },

  /** 读取 Agent 在最终评估后生成的结束总结、证据和改进任务。 */
  async getInterviewClosure(sessionId: string): Promise<InterviewClosure> {
    return request.get<InterviewClosure>(`/api/interview/sessions/${sessionId}/closure`);
  },

  /**
   * 导出简历分析报告PDF
   */
  async exportAnalysisPdf(resumeId: number): Promise<Blob> {
    return request.download(`/api/resumes/${resumeId}/export`);
  },

  /**
   * 导出面试报告PDF
   */
  async exportInterviewPdf(sessionId: string): Promise<Blob> {
    return request.download(`/api/interview/sessions/${sessionId}/export`);
  },

  /**
   * 删除简历
   */
  async deleteResume(id: number): Promise<void> {
    return request.delete(`/api/resumes/${id}`);
  },

  /**
   * 删除面试记录
   */
  async deleteInterview(sessionId: string): Promise<void> {
    return request.delete(`/api/interview/sessions/${sessionId}`);
  },

  /**
   * 获取简历统计信息
   */
  async getStatistics(): Promise<ResumeStats> {
    return request.get<ResumeStats>('/api/resumes/statistics');
  },

  /**
   * 重新分析简历
   */
  async reanalyze(id: number): Promise<void> {
    return request.post(`/api/resumes/${id}/reanalyze`);
  },
};
