import request from './request';

export interface ResumeImprovementPlan {
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
  createdAt: string;
}

export interface CreateResumeImprovementPlanRequest {
  matchReportId: number;
}

export const resumeImprovementPlanApi = {
  list(matchReportId?: number) {
    return request.get<ResumeImprovementPlan[]>('/api/resume-improvement-plans', {
      params: matchReportId ? { matchReportId } : undefined,
    });
  },

  create(data: CreateResumeImprovementPlanRequest) {
    return request.post<ResumeImprovementPlan>('/api/resume-improvement-plans', data, {
      timeout: 180000,
    });
  },
};
