import {useEffect, useRef, useState} from 'react';
import {motion} from 'framer-motion';
import {interviewApi} from '../api/interview';
import {agentApi} from '../api/agent';
import ConfirmDialog from '../components/ConfirmDialog';
import InterviewChatPanel from '../components/InterviewChatPanel';
import InterviewPageHeader from '../components/InterviewPageHeader';
import type {
  InterviewBlueprint,
  InterviewAgentDecision,
  InterviewQuestion,
  InterviewSession,
  InterviewIntent,
  InterviewTrainingMode,
} from '../types/interview';
import type {Difficulty} from '../components/UnifiedInterviewModal';
import type {CategoryDTO} from '../api/skill';
import { CUSTOM_SKILL_ID } from '../hooks/useInterviewConfig';
import { BrainCircuit, GitBranch, Gauge, Target } from 'lucide-react';

interface Message {
  type: 'interviewer' | 'user';
  content: string;
  category?: string;
  questionIndex?: number;
}

interface InterviewProps {
  resumeText: string;
  resumeId?: number;
  sessionIdToResume?: string;
  initialConfig?: {
    questionCount?: number;
    llmProvider?: string;
    skillId?: string;
    difficulty?: Difficulty;
    customCategories?: CategoryDTO[];
    jdText?: string;
    jobId?: number;
    matchReportId?: number;
    trainingMode?: InterviewTrainingMode;
    userFocus?: string;
  };
  onBack: () => void;
  onInterviewComplete: () => void;
}

export default function Interview({
  resumeText,
  resumeId,
  sessionIdToResume,
  initialConfig,
  onBack,
  onInterviewComplete,
}: InterviewProps) {
  const [session, setSession] = useState<InterviewSession | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState<InterviewQuestion | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [answer, setAnswer] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [showCompleteConfirm, setShowCompleteConfirm] = useState(false);
  const [lastDecision, setLastDecision] = useState<InterviewAgentDecision | null>(null);
  const [awaitingContinue, setAwaitingContinue] = useState(false);
  const startedRef = useRef(false);

  const questionCount = initialConfig?.questionCount ?? 8;
  const llmProvider = initialConfig?.llmProvider ?? '';
  const skillId = initialConfig?.skillId ?? 'java-backend';
  const difficulty = initialConfig?.difficulty ?? 'mid';
  const customCategories = initialConfig?.customCategories;
  const jdText = initialConfig?.jdText;
  const jobId = initialConfig?.jobId;
  const matchReportId = initialConfig?.matchReportId;
  const trainingMode = initialConfig?.trainingMode
    ?? (matchReportId ? 'JOB_TARGETED' : resumeId ? 'RESUME_DEFENSE' : 'GENERAL');
  const userFocus = initialConfig?.userFocus;

  // 自动开始面试（恢复已有会话 或 创建新会话）
  useEffect(() => {
    if (!startedRef.current) {
      startedRef.current = true;
      if (sessionIdToResume) {
        resumeExistingSession(sessionIdToResume);
      } else {
        startInterview();
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const startInterview = async () => {
    setIsCreating(true);
    setError('');

    try {
      const newSession = await agentApi.createInterviewSession({
        resumeText,
        questionCount,
        resumeId,
        forceCreate: true,
        llmProvider,
        skillId,
        difficulty,
        customCategories: skillId === CUSTOM_SKILL_ID ? customCategories : undefined,
        jdText: skillId === CUSTOM_SKILL_ID ? jdText : undefined,
        jobId,
        matchReportId,
        trainingMode,
        userFocus,
      });

      initSession(newSession);
    } catch (err) {
      setError('创建面试失败，请重试');
      console.error(err);
    } finally {
      setIsCreating(false);
    }
  };

  const resumeExistingSession = async (sessionId: string) => {
    setIsCreating(true);
    setError('');

    try {
      const existingSession = await interviewApi.getSession(sessionId);
      initSession(existingSession);

      // 恢复已填写的答案
      const currentQ = existingSession.questions[existingSession.currentQuestionIndex];
      if (currentQ?.userAnswer) {
        setAnswer(currentQ.userAnswer);
      }
    } catch (err) {
      setError('恢复面试失败，请重试');
      console.error(err);
    } finally {
      setIsCreating(false);
    }
  };

  const initSession = (s: InterviewSession) => {
    setSession(s);

    if (s.questions.length > 0) {
      const idx = Math.min(s.currentQuestionIndex, s.questions.length - 1);
      const currentQ = s.questions[idx];
      setCurrentQuestion(currentQ);

      // 重建消息历史
      const restoredMessages: Message[] = [];
      for (const q of s.questions) {
        // Agent 换题时会跳过低价值追问，恢复页面时不展示这些未实际问过的题。
        if (!q.userAnswer && q.questionIndex !== currentQ.questionIndex) {
          continue;
        }
        restoredMessages.push({
          type: 'interviewer',
          content: q.question,
          category: q.category,
          questionIndex: q.questionIndex
        });
        if (q.userAnswer) {
          restoredMessages.push({
            type: 'user',
            content: q.userAnswer
          });
        }
      }
      setMessages(restoredMessages);
    }
  };

  const submitInteraction = async (intent: InterviewIntent, content = '') => {
    if (!session || !currentQuestion || (intent === 'AUTO' && !content.trim())) return;

    setIsSubmitting(true);

    const controlLabels: Partial<Record<InterviewIntent, string>> = {
      HINT: '请给我一点提示',
      EXPLAIN: '请讲解这道题',
      SKIP: '跳过这道题',
      CONTINUE: '继续下一题',
      END: '结束本次面试',
    };
    const displayContent = content.trim() || controlLabels[intent] || '';
    const userMessage: Message = {
      type: 'user',
      content: displayContent,
    };
    setMessages(prev => [...prev, userMessage]);

    try {
      const response = await agentApi.submitInterviewTurn(
        session.sessionId,
        currentQuestion.questionIndex,
        content.trim(),
        intent,
      );

      const assistanceIntent = response.intent === 'HINT' || response.intent === 'EXPLAIN';
      if (!assistanceIntent || intent === 'AUTO') {
        setAnswer('');
      }
      setLastDecision(response.decision);
      setAwaitingContinue(response.intent === 'EXPLAIN');

      if (response.assistantMessage) {
        setMessages(prev => [...prev, {
          type: 'interviewer',
          content: response.assistantMessage!,
          category: response.intent === 'HINT' ? '教练提示' : '题目讲解',
          questionIndex: currentQuestion.questionIndex,
        }]);
      }

      const answeredNormally = response.intent === 'ANSWER' && response.decision !== null;
      setSession(previous => {
        if (!previous) return previous;
        const updatedQuestions = answeredNormally
          ? previous.questions.map(question => question.questionIndex === currentQuestion.questionIndex
              ? {
                  ...question,
                  userAnswer: content.trim(),
                  score: response.decision!.answerScore,
                  feedback: response.decision!.feedback,
                }
              : question)
          : [...previous.questions];
        // Agent 增量出题后立即追加到本地会话，后续轮次和页面状态保持一致。
        if (response.nextQuestion
            && !updatedQuestions.some(question =>
              question.questionIndex === response.nextQuestion!.questionIndex)) {
          updatedQuestions.push(response.nextQuestion);
        }
        return {
          ...previous,
          currentQuestionIndex: response.nextQuestion?.questionIndex
            ?? (response.completed ? previous.currentQuestionIndex + 1 : previous.currentQuestionIndex),
          status: response.completed ? 'COMPLETED' : 'IN_PROGRESS',
          questions: updatedQuestions,
        };
      });

      const staysOnCurrentQuestion = assistanceIntent;
      if (!response.completed && response.nextQuestion && !staysOnCurrentQuestion) {
        setCurrentQuestion(response.nextQuestion);
        setMessages(prev => [...prev, {
          type: 'interviewer',
          content: response.nextQuestion!.question,
          category: response.nextQuestion!.category,
          questionIndex: response.nextQuestion!.questionIndex
        }]);
      } else {
        if (response.completed) {
          onInterviewComplete();
        }
      }
    } catch (err) {
      setError('面试操作失败，请重试');
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSubmitAnswer = async () => {
    await submitInteraction('AUTO', answer);
  };

  const handleCompleteEarly = async () => {
    setShowCompleteConfirm(false);
    await submitInteraction('END');
  };

  // 加载中
  if (isCreating) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="text-center">
          <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full mx-auto mb-4 animate-spin" />
          <p className="text-slate-500 dark:text-slate-400">正在生成面试题目...</p>
        </div>
      </div>
    );
  }

  // 错误状态
  if (error && !session) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="text-center">
          <p className="text-red-500 dark:text-red-400 mb-4">{error}</p>
          <div className="flex gap-3 justify-center">
            <button
              onClick={startInterview}
              className="px-5 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
            >
              重试
            </button>
            <button
              onClick={onBack}
              className="px-5 py-2 bg-slate-200 dark:bg-slate-700 text-slate-700 dark:text-slate-300 rounded-lg hover:bg-slate-300 dark:hover:bg-slate-600"
            >
              返回
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!session || !currentQuestion) return null;

  return (
    <div className="pb-10">
      <InterviewPageHeader
        title="模拟面试"
        subtitle="认真回答每个问题，展示您的实力"
        icon={(
          <svg className="w-6 h-6 text-white" viewBox="0 0 24 24" fill="none">
            <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M19 10v2a7 7 0 0 1-14 0v-2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <line x1="12" y1="19" x2="12" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <line x1="8" y1="23" x2="16" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        )}
      />

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.3 }}
      >
        {session.blueprint && <InterviewBlueprintCard blueprint={session.blueprint} />}
        {lastDecision && <InterviewDecisionCard decision={lastDecision} />}
        <InterviewChatPanel
          session={session}
          currentQuestion={currentQuestion}
          messages={messages}
          answer={answer}
          onAnswerChange={setAnswer}
          onSubmit={handleSubmitAnswer}
          onHint={() => submitInteraction('HINT')}
          onExplain={() => submitInteraction('EXPLAIN')}
          onSkip={() => submitInteraction('SKIP')}
          onContinue={() => submitInteraction('CONTINUE')}
          awaitingContinue={awaitingContinue}
          onCompleteEarly={handleCompleteEarly}
          isSubmitting={isSubmitting}
          showCompleteConfirm={showCompleteConfirm}
          onShowCompleteConfirm={setShowCompleteConfirm}
        />
      </motion.div>

      {/* 提前交卷确认对话框 */}
      <ConfirmDialog
        open={showCompleteConfirm}
        title="提前交卷"
        message="确定结束本次面试吗？当前题不会被评分，已回答内容将生成部分评价。"
        confirmText="确定交卷"
        cancelText="取消"
        confirmVariant="warning"
        loading={isSubmitting}
        onConfirm={handleCompleteEarly}
        onCancel={() => setShowCompleteConfirm(false)}
      />
    </div>
  );
}

const MODE_LABELS: Record<InterviewTrainingMode, string> = {
  GENERAL: '综合摸底',
  JOB_TARGETED: '岗位定向',
  FOCUS_DRILL: '专项强化',
  RESUME_DEFENSE: '简历深挖',
};

const QUESTION_TYPE_LABELS: Record<InterviewBlueprint['questionTypes'][number], string> = {
  CONCEPT: '原理',
  PROJECT_EVIDENCE: '项目证据',
  SCENARIO_DESIGN: '场景设计',
  TROUBLESHOOTING: '故障排查',
};

function InterviewBlueprintCard({ blueprint }: { blueprint: InterviewBlueprint }) {
  return (
    <aside className="mb-4 rounded-xl border border-cyan-200 bg-cyan-50/70 px-4 py-3 dark:border-cyan-900/50 dark:bg-cyan-950/20">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-sm font-semibold text-cyan-900 dark:text-cyan-100">
          <Target className="h-4 w-4" />
          {MODE_LABELS[blueprint.mode]} · {blueprint.objective}
        </div>
        <span className="text-xs text-cyan-700 dark:text-cyan-300">
          每个主题最多追问 {blueprint.maxFollowUpsPerTopic} 次
        </span>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        {blueprint.focusTopics.map(topic => (
          <span key={topic} className="rounded-md bg-white px-2 py-1 text-xs text-slate-700 shadow-sm dark:bg-slate-900 dark:text-slate-200">
            {topic}
          </span>
        ))}
        {blueprint.questionTypes.map(type => (
          <span key={type} className="rounded-md border border-cyan-200 px-2 py-1 text-xs text-cyan-700 dark:border-cyan-800 dark:text-cyan-300">
            {QUESTION_TYPE_LABELS[type]}
          </span>
        ))}
        {blueprint.targetRequirementIds.map(id => (
          <span key={id} className="rounded-md bg-slate-900 px-2 py-1 text-xs text-white">
            {id}
          </span>
        ))}
      </div>
      <p className="mt-3 text-xs leading-5 text-slate-600 dark:text-slate-400">{blueprint.rationale}</p>
    </aside>
  );
}

const ACTION_LABELS: Record<InterviewAgentDecision['action'], string> = {
  FOLLOW_UP: '继续追问',
  SWITCH_TOPIC: '切换方向',
  ADJUST_DIFFICULTY: '调整难度',
  END_INTERVIEW: '结束面试',
};

function InterviewDecisionCard({ decision }: { decision: InterviewAgentDecision }) {
  return (
    <motion.aside
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      className="mb-4 overflow-hidden rounded-xl border border-slate-200 bg-slate-950 text-slate-100 shadow-lg shadow-slate-950/10 dark:border-slate-700"
    >
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 px-4 py-3">
        <div className="flex items-center gap-2">
          <BrainCircuit className="h-4 w-4 text-cyan-400" />
          <span className="text-xs font-semibold tracking-[0.16em] text-slate-300">AGENT TURN DECISION</span>
        </div>
        <div className="flex items-center gap-2 text-xs">
          <span className="inline-flex items-center gap-1 rounded-md bg-cyan-400/10 px-2 py-1 font-medium text-cyan-300">
            <Gauge className="h-3.5 w-3.5" />
            {decision.answerScore} 分
          </span>
          <span className="inline-flex items-center gap-1 rounded-md bg-amber-400/10 px-2 py-1 font-medium text-amber-300">
            <GitBranch className="h-3.5 w-3.5" />
            {ACTION_LABELS[decision.action]}
          </span>
          {decision.targetRequirementId && (
            <span className="inline-flex items-center gap-1 rounded-md bg-slate-800 px-2 py-1 text-slate-300">
              <Target className="h-3.5 w-3.5" />
              {decision.targetRequirementId}
            </span>
          )}
        </div>
      </div>
      <div className="grid gap-3 px-4 py-3 text-sm md:grid-cols-2">
        <div>
          <p className="mb-1 text-[11px] uppercase tracking-wider text-slate-500">回答反馈</p>
          <p className="leading-6 text-slate-200">{decision.feedback}</p>
        </div>
        <div>
          <p className="mb-1 text-[11px] uppercase tracking-wider text-slate-500">决策依据</p>
          <p className="leading-6 text-slate-300">{decision.rationale}</p>
        </div>
      </div>
    </motion.aside>
  );
}
