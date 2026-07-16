import request from './request';
import { streamSse } from './stream';
import { getAuthToken } from '../utils/authStorage';
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

  submitInterviewTurnStream(
    sessionId: string,
    questionIndex: number,
    answer: string,
    intent: InterviewIntent,
    callbacks: {
      onProgress: (progress: { phase: string; label: string }) => void;
      onAssistantDelta: (chunk: string) => void;
      onResult: (result: AdaptiveInterviewTurnResult) => void;
      onError: (error: Error) => void;
    },
  ) {
    const token = getAuthToken();
    return streamSse({
      url: `/api/agent/interviews/${sessionId}/turns/stream`,
      init: {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ questionIndex, answer, intent }),
      },
      onMessage: () => undefined,
      onEvent: (eventName, data) => {
        if (eventName === 'progress') {
          callbacks.onProgress(JSON.parse(data) as { phase: string; label: string });
        } else if (eventName === 'assistant_delta') {
          const payload = JSON.parse(data) as { text: string };
          callbacks.onAssistantDelta(payload.text);
        } else if (eventName === 'result') {
          callbacks.onResult(JSON.parse(data) as AdaptiveInterviewTurnResult);
        }
      },
      onComplete: () => undefined,
      onError: callbacks.onError,
      parseMode: 'event',
      trimDataPrefixSpace: false,
      dataJoiner: '',
    });
  },
};
