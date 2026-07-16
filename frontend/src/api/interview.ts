import { request } from './request';
import type { InterviewSession } from '../types/interview';

export interface TextSessionMeta {
  sessionId: string;
  skillId: string;
  difficulty: string;
  resumeId: number | null;
  jobId: number | null;
  matchReportId: number | null;
  totalQuestions: number;
  status: string;
  evaluateStatus: string | null;
  evaluateError: string | null;
  overallScore: number | null;
  createdAt: string;
  completedAt: string | null;
}

export const interviewApi = {
  /**
   * 列出所有文字面试会话
   */
  async listSessions(jobId?: number): Promise<TextSessionMeta[]> {
    return request.get<TextSessionMeta[]>('/api/interview/sessions', {
      params: jobId ? { jobId } : undefined,
    });
  },

  /**
   * 获取会话信息
   */
  async getSession(sessionId: string): Promise<InterviewSession> {
    return request.get<InterviewSession>(`/api/interview/sessions/${sessionId}`);
  },

};
