// 面试相关类型定义

import type { CategoryDTO } from '../api/skill';

export interface InterviewSession {
  sessionId: string;
  resumeText: string;
  totalQuestions: number;
  currentQuestionIndex: number;
  questions: InterviewQuestion[];
  status: 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'EVALUATED';
  blueprint?: InterviewBlueprint | null;
  endReason?: string | null;
  completionType?: 'COMPLETE' | 'PARTIAL' | null;
  coveredTargets?: string[];
  unverifiedTargets?: string[];
}

export type InterviewTrainingMode =
  | 'GENERAL'
  | 'JOB_TARGETED'
  | 'FOCUS_DRILL'
  | 'RESUME_DEFENSE';

export interface InterviewBlueprint {
  mode: InterviewTrainingMode;
  objective: string;
  targetRequirementIds: string[];
  focusTopics: string[];
  questionTypes: Array<'CONCEPT' | 'PROJECT_EVIDENCE' | 'SCENARIO_DESIGN' | 'TROUBLESHOOTING'>;
  avoidTopics: string[];
  difficulty: string;
  questionCount: number;
  maxFollowUpsPerTopic: number;
  rationale: string;
}

export interface InterviewQuestion {
  questionIndex: number;
  question: string;
  type: string;
  category: string;
  userAnswer: string | null;
  score: number | null;
  feedback: string | null;
  topicSummary?: string | null;
  isFollowUp?: boolean;
  followUp?: boolean;
  parentQuestionIndex?: number | null;
  requirementId?: string | null;
}

export type InterviewAgentAction =
  | 'FOLLOW_UP'
  | 'SWITCH_TOPIC'
  | 'ADJUST_DIFFICULTY'
  | 'END_INTERVIEW';

export type InterviewIntent =
  | 'AUTO'
  | 'ANSWER'
  | 'END'
  | 'SKIP'
  | 'HINT'
  | 'EXPLAIN'
  | 'CONTINUE';

export interface InterviewAgentDecision {
  questionIndex: number;
  action: InterviewAgentAction;
  rationale: string;
  answerScore: number;
  feedback: string;
  difficultyAdjustment: 'KEEP' | 'HARDER' | 'EASIER';
  targetQuestionIndex: number | null;
  targetRequirementId: string | null;
  createdAt: string;
}

export interface AdaptiveInterviewTurnResult {
  sessionId: string;
  completed: boolean;
  nextQuestion: InterviewQuestion | null;
  decision: InterviewAgentDecision | null;
  answeredCount: number;
  totalQuestions: number;
  intent: Exclude<InterviewIntent, 'AUTO'>;
  assistantMessage?: string | null;
}

export interface CreateInterviewRequest {
  resumeText: string;
  questionCount: number;
  resumeId?: number;
  forceCreate?: boolean;
  llmProvider?: string;
  skillId: string;
  difficulty?: string;
  customCategories?: CategoryDTO[];
  jdText?: string;
  jobId?: number;
  matchReportId?: number;
  trainingMode?: InterviewTrainingMode;
  userFocus?: string;
}
