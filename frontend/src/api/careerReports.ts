import request from './request';
import type { JobEvaluation } from './history';

export interface CareerReport {
  matchReportId: number;
  job: {
    id: number;
    title: string;
    company?: string | null;
    location?: string | null;
    sourceUrl?: string | null;
    jdText: string;
  };
  resume: {
    id: number;
    filename: string;
  };
  match: {
    overallScore: number;
    skillScore: number;
    projectScore: number;
    keywordScore: number;
    summary: string;
    matchedHighlights: string[];
    gaps: string[];
    actionItems: string[];
    createdAt: string;
  };
  latestInterview?: {
    sessionId: string;
    overallScore?: number | null;
    overallFeedback?: string | null;
    jobEvaluation?: JobEvaluation | null;
    completedAt?: string | null;
  } | null;
  improvementPlan?: {
    id: number;
    readinessScore: number;
    summary: string;
    priorityFixes: string[];
    resumeRewriteBullets: string[];
    projectUpgradeTasks: string[];
    interviewPracticeTasks: string[];
    learningTasks: string[];
    createdAt: string;
  } | null;
  generatedAt: string;
}

export const careerReportApi = {
  get(matchReportId: number) {
    return request.get<CareerReport>(`/api/career-reports/${matchReportId}`);
  },

  exportPdf(matchReportId: number) {
    return request.download(`/api/career-reports/${matchReportId}/export`);
  },
};
