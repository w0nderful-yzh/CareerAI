import request from './request';
import type { AgentRun, CreateAgentRunRequest } from '../types/agent';
import type {
  AdaptiveInterviewTurnResult,
  CreateInterviewRequest,
  InterviewSession,
  InterviewIntent,
} from '../types/interview';

export const agentApi = {
  createInterviewSession(data: CreateInterviewRequest) {
    return request.post<InterviewSession>('/api/agent/interviews/sessions', data, {
      timeout: 180000,
    });
  },

  createRun(data: CreateAgentRunRequest) {
    return request.post<AgentRun>('/api/agent/runs', data, {
      timeout: 180000,
    });
  },

  getRun(runId: string) {
    return request.get<AgentRun>(`/api/agent/runs/${runId}`);
  },

  resumeRun(runId: string) {
    return request.post<AgentRun>(`/api/agent/runs/${runId}/resume`, undefined, {
      timeout: 180000,
    });
  },

  submitInterviewTurn(
    sessionId: string,
    questionIndex: number,
    answer = '',
    intent: InterviewIntent = 'AUTO',
  ) {
    return request.post<AdaptiveInterviewTurnResult>(
      `/api/agent/interviews/${sessionId}/turns`,
      { questionIndex, answer, intent },
      { timeout: 180000 },
    );
  },
};
