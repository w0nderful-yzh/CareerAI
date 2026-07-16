export type AgentRunStatus =
  | 'PLANNING'
  | 'RUNNING'
  | 'WAITING_ASYNC'
  | 'WAITING_APPROVAL'
  | 'PAUSED'
  | 'PARTIALLY_COMPLETED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type AgentStepStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'SKIPPED';

export interface AgentPlanStep {
  id: string;
  title: string;
  status: AgentStepStatus;
}

export interface AgentArtifact {
  type: string;
  [key: string]: unknown;
}

export interface BusinessContextArtifact extends AgentArtifact {
  type: 'business_context';
  resumeId: number;
  resumeFilename: string;
  resumeLatestScore?: number | null;
  jobId: number;
  jobTitle: string;
  selectionReason: string;
}

export interface JobMatchReportArtifact extends AgentArtifact {
  type: 'job_match_report';
  id: number;
  resumeId: number;
  resumeFilename: string;
  jobId: number;
  jobTitle: string;
  overallScore: number;
  skillScore: number;
  projectScore: number;
  keywordScore: number;
  summary: string;
  matchedHighlights: string[];
  gaps: string[];
  actionItems: string[];
  evidenceMappings: RequirementEvidence[];
}

export type EvidenceCoverageType =
  | 'SUPPORTED'
  | 'EXPRESSION_GAP'
  | 'EVIDENCE_GAP'
  | 'CAPABILITY_GAP';

export interface RequirementEvidence {
  requirement: {
    id: string;
    category: string;
    description: string;
    importance: 'HIGH' | 'MEDIUM' | 'LOW';
    sourceQuote: string;
  };
  resumeEvidence: Array<{
    sourceType: string;
    sourceLocation: string;
    quote: string;
    strength: 'STRONG' | 'MODERATE' | 'WEAK';
  }>;
  coverageType: EvidenceCoverageType;
  confidence: number;
  reasoning: string;
  recommendedAction: string;
}

export type PreparationStrategy =
  | 'RESUME_FIRST'
  | 'PROJECT_FIRST'
  | 'INTERVIEW_FIRST'
  | 'BALANCED';

export interface PreparationDecisionArtifact extends AgentArtifact {
  type: 'preparation_decision';
  action: 'CREATE_IMPROVEMENT_PLAN' | 'COMPLETE_WITH_MATCH_REPORT';
  strategy: PreparationStrategy;
  rationale: string;
  prioritizedGaps: string[];
  supportingEvidence: string[];
  interviewFocus: string[];
  selectedTool?: string | null;
}

export interface ResumeImprovementPlanArtifact extends AgentArtifact {
  type: 'resume_improvement_plan';
  id: number;
  matchReportId: number;
  resumeId: number;
  resumeFilename: string;
  jobId: number;
  jobTitle: string;
  readinessScore: number;
  summary: string;
  priorityFixes: string[];
  resumeRewriteBullets: string[];
  projectUpgradeTasks: string[];
  interviewPracticeTasks: string[];
  learningTasks: string[];
  preparationTasks: PreparationTask[];
}

export interface PreparationTask {
  id: string;
  category: 'RESUME' | 'PROJECT' | 'LEARNING' | 'INTERVIEW';
  title: string;
  priority: 'P0' | 'P1' | 'P2';
  suggestedDays: number;
  verificationMethod: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
  relatedRequirementIds: string[];
}

export interface AgentRunError {
  stepId: string;
  code: number;
  message: string;
  retryable: boolean;
}

export interface AgentRun {
  id: string;
  user_id: string;
  goal: string;
  constraints: Record<string, unknown>;
  status: AgentRunStatus;
  plan: AgentPlanStep[];
  artifacts: AgentArtifact[];
  errors: AgentRunError[];
  pause_reason?: string | null;
  selected_resume_id?: number | null;
  job_id?: number | null;
  match_task_id?: number | null;
  match_report_id?: number | null;
  improvement_plan_id?: number | null;
  poll_count: number;
}

export interface CreateAgentRunRequest {
  goal: string;
  constraints: {
    jobId: number;
    resumeId: number;
  };
}
