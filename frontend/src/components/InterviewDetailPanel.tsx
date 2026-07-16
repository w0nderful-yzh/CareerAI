import {useMemo, useState} from 'react';
import {AnimatePresence, motion} from 'framer-motion';
import {getScoreColor} from '../utils/score';
import type {AnswerItem, InterviewDetail, InterviewTurnEvaluation} from '../api/history';
import InterviewClosureSection from './InterviewClosureSection';

interface InterviewDetailPanelProps {
  interview: InterviewDetail;
}

/**
 * 面试详情面板组件
 */
export default function InterviewDetailPanel({ interview }: InterviewDetailPanelProps) {
  // 默认展开所有题目
  const [expandedQuestions, setExpandedQuestions] = useState<Set<number>>(() => {
    const allIndices = new Set<number>();
    if (interview.answers) {
      interview.answers.forEach((_, idx) => allIndices.add(idx));
    }
    return allIndices;
  });

  const toggleQuestion = (index: number) => {
    setExpandedQuestions(prev => {
      const newSet = new Set(prev);
      if (newSet.has(index)) {
        newSet.delete(index);
      } else {
        newSet.add(index);
      }
      return newSet;
    });
  };

  // 计算圆环进度
  const { scorePercent, circumference, strokeDashoffset } = useMemo(() => {
    const percent = interview.overallScore !== null ? (interview.overallScore / 100) * 100 : 0;
    const circ = 2 * Math.PI * 54; // r=54
    const offset = circ - (percent / 100) * circ;
    return { scorePercent: percent, circumference: circ, strokeDashoffset: offset };
  }, [interview.overallScore]);

  return (
      <motion.div
      className="space-y-6"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      {/* 评分卡片 */}
      <ScoreCard
        score={interview.overallScore}
        feedback={interview.overallFeedback}
        scorePercent={scorePercent}
        circumference={circumference}
        strokeDashoffset={strokeDashoffset}
      />

      {(interview.completionType || interview.coveredTargets?.length || interview.unverifiedTargets?.length) && (
        <CompletionEvidenceSection interview={interview} />
      )}

      {(interview.status === 'EVALUATED' || interview.evaluateStatus === 'COMPLETED') && (
        <InterviewClosureSection sessionId={interview.sessionId} />
      )}

      {/* 岗位化评价 */}
      {interview.jobEvaluation && (
        <JobEvaluationSection evaluation={interview.jobEvaluation} />
      )}

      {/* 表现优势 */}
      {interview.strengths && interview.strengths.length > 0 && (
        <StrengthsSection strengths={interview.strengths} />
      )}

      {/* 改进建议 */}
      {interview.improvements && interview.improvements.length > 0 && (
        <ImprovementsSection improvements={interview.improvements} />
      )}

      {/* 问答记录详情 */}
        <QuestionsSection
        answers={interview.answers || []}
        expandedQuestions={expandedQuestions}
        toggleQuestion={toggleQuestion}
      />
    </motion.div>
  );
}

const END_REASON_LABELS: Record<string, string> = {
  USER_REQUESTED: '用户主动结束',
  MANUAL_BUTTON: '手动结束',
  PLAN_COMPLETED: '计划已完成',
  QUESTION_LIMIT: '达到题目上限',
  TIME_LIMIT: '达到时间上限',
  SUFFICIENT_EVIDENCE: '能力证据已充分',
  LOW_INFORMATION: '连续低信息回答',
  OFF_TOPIC: '回答持续偏题',
  SYSTEM_ERROR: '系统异常中止',
};

function CompletionEvidenceSection({ interview }: { interview: InterviewDetail }) {
  const complete = interview.completionType === 'COMPLETE';
  return (
    <motion.section
      className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.05 }}
    >
      <div className="flex flex-col gap-3 border-b border-slate-100 px-6 py-5 dark:border-slate-700 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">Interview coverage</p>
          <h4 className="mt-1 text-lg font-bold text-slate-900 dark:text-white">本次面试证据覆盖</h4>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className={`rounded-full px-3 py-1 text-xs font-bold ${complete
            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300'
            : 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300'}`}>
            {complete ? '完整完成' : '部分完成'}
          </span>
          {interview.endReason && (
            <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600 dark:bg-slate-700 dark:text-slate-300">
              {END_REASON_LABELS[interview.endReason] || interview.endReason}
            </span>
          )}
        </div>
      </div>
      <div className="grid gap-5 p-6 md:grid-cols-2">
        <TargetList title="已验证目标" items={interview.coveredTargets || []} tone="covered" />
        <TargetList title="尚未验证" items={interview.unverifiedTargets || []} tone="pending" />
      </div>
    </motion.section>
  );
}

function TargetList({ title, items, tone }: { title: string; items: string[]; tone: 'covered' | 'pending' }) {
  const covered = tone === 'covered';
  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h5 className="text-sm font-bold text-slate-800 dark:text-white">{title}</h5>
        <span className="font-mono text-xs text-slate-400">{items.length}</span>
      </div>
      <div className="flex flex-wrap gap-2">
        {items.length ? items.map(item => (
          <span
            key={`${tone}-${item}`}
            className={`rounded-lg border px-3 py-1.5 text-xs font-medium ${covered
              ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/50 dark:bg-emerald-900/20 dark:text-emerald-300'
              : 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/50 dark:bg-amber-900/20 dark:text-amber-300'}`}
          >
            {item}
          </span>
        )) : <span className="text-sm text-slate-400">暂无</span>}
      </div>
    </div>
  );
}

function JobEvaluationSection({
  evaluation,
}: {
  evaluation: NonNullable<InterviewDetail['jobEvaluation']>;
}) {
  return (
    <motion.div
      className="overflow-hidden rounded-2xl border border-indigo-100 bg-white shadow-sm dark:border-indigo-900/40 dark:bg-slate-800"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.08 }}
    >
      <div className="bg-gradient-to-r from-slate-950 via-indigo-950 to-slate-900 px-6 py-5 text-white">
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-indigo-200">Job Fit Review</p>
            <h4 className="mt-1 text-xl font-bold">{evaluation.targetJobTitle || '目标岗位复盘'}</h4>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-white/80">{evaluation.conclusion}</p>
          </div>
          <div className="flex h-20 w-20 shrink-0 flex-col items-center justify-center rounded-2xl bg-white/10 ring-1 ring-white/20">
            <span className="text-3xl font-black">{evaluation.jdCoverageScore}</span>
            <span className="text-xs text-white/60">JD覆盖</span>
          </div>
        </div>
      </div>

      <div className="grid gap-4 p-6 md:grid-cols-2">
        <JobEvaluationList title="已覆盖能力" items={evaluation.jdCoverage} tone="good" />
        <JobEvaluationList title="暴露短板" items={evaluation.exposedGaps} tone="gap" />
        <JobEvaluationList title="简历可沉淀表达" items={evaluation.resumeRewriteSuggestions} tone="resume" />
        <JobEvaluationList title="下一步行动" items={evaluation.nextActions} tone="action" />
      </div>
    </motion.div>
  );
}

function JobEvaluationList({
  title,
  items,
  tone,
}: {
  title: string;
  items: string[];
  tone: 'good' | 'gap' | 'resume' | 'action';
}) {
  const toneClassName = {
    good: 'bg-emerald-500',
    gap: 'bg-rose-500',
    resume: 'bg-indigo-500',
    action: 'bg-amber-500',
  }[tone];

  return (
    <div className="rounded-2xl bg-slate-50 p-4 dark:bg-slate-900/60">
      <h5 className="mb-3 text-sm font-bold text-slate-800 dark:text-white">{title}</h5>
      <div className="space-y-2.5">
        {(items?.length ? items : ['暂无数据']).map((item, index) => (
          <div key={`${title}-${index}`} className="flex items-start gap-2.5 text-sm leading-6 text-slate-600 dark:text-slate-300">
            <span className={`mt-2 h-2 w-2 shrink-0 rounded-full ${toneClassName}`} />
            <span>{item}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// 评分卡片组件
function ScoreCard({
  score,
  feedback,
  // scorePercent, // 暂时未使用
  circumference,
  strokeDashoffset
}: {
  score: number | null;
  feedback: string | null;
  scorePercent: number;
  circumference: number;
  strokeDashoffset: number;
}) {
  return (
    <div className="bg-gradient-to-br from-violet-600 via-purple-600 to-indigo-700 rounded-2xl p-8 text-white">
      <div className="flex flex-col items-center text-center">
        {/* 圆环进度条 */}
        <div className="relative w-32 h-32 mb-6">
          <svg className="w-32 h-32 transform -rotate-90" viewBox="0 0 120 120">
            <circle
              cx="60"
              cy="60"
              r="54"
              stroke="rgba(255,255,255,0.2)"
              strokeWidth="8"
              fill="none"
            />
            <motion.circle
              cx="60"
              cy="60"
              r="54"
              stroke="white"
              strokeWidth="8"
              fill="none"
              strokeLinecap="round"
              strokeDasharray={circumference}
              initial={{ strokeDashoffset: circumference }}
              animate={{ strokeDashoffset }}
              transition={{ duration: 1.5, ease: "easeOut" }}
            />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <motion.span
              className="text-4xl font-bold"
              initial={{ opacity: 0, scale: 0.5 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: 0.5 }}
            >
              {score ?? '-'}
            </motion.span>
            <span className="text-sm text-white/70">总分</span>
          </div>
        </div>

        <h3 className="text-2xl font-bold mb-3">面试评估</h3>
        <p className="text-white/90 max-w-2xl leading-relaxed">
          {feedback || '表现良好，展示了扎实的技术基础。'}
        </p>
      </div>
    </div>
  );
}

// 优势部分组件
function StrengthsSection({ strengths }: { strengths: string[] }) {
  return (
      <motion.div
          className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-sm"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.1 }}
    >
        <h4 className="font-semibold text-emerald-600 dark:text-emerald-400 mb-4 flex items-center gap-2">
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <polyline points="22,4 12,14.01 9,11.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
        表现优势
      </h4>
      <ul className="space-y-3">
        {strengths.map((s: string, i: number) => (
            <li key={i} className="text-slate-700 dark:text-slate-300 flex items-start gap-3">
            <span className="w-2 h-2 bg-primary-500 rounded-full mt-2 flex-shrink-0"></span>
            <span>{s}</span>
          </li>
        ))}
      </ul>
    </motion.div>
  );
}

// 改进建议部分组件
function ImprovementsSection({ improvements }: { improvements: string[] }) {
  return (
      <motion.div
          className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-sm"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.2 }}
    >
        <h4 className="font-semibold text-amber-600 dark:text-amber-400 mb-4 flex items-center gap-2">
        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
          <line x1="12" y1="8" x2="12" y2="12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          <line x1="12" y1="16" x2="12.01" y2="16" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </svg>
        改进建议
      </h4>
      <ul className="space-y-3">
        {improvements.map((s: string, i: number) => (
            <li key={i} className="text-slate-700 dark:text-slate-300 flex items-start gap-3">
            <span className="w-2 h-2 bg-amber-500 rounded-full mt-2 flex-shrink-0"></span>
            <span>{s}</span>
          </li>
        ))}
      </ul>
    </motion.div>
  );
}

// 问答部分组件
function QuestionsSection({
  answers,
  expandedQuestions,
  toggleQuestion
}: {
  answers: AnswerItem[];
  expandedQuestions: Set<number>;
  toggleQuestion: (index: number) => void;
}) {
  return (
    <div>
      <h4 className="font-semibold text-slate-800 dark:text-white mb-4 flex items-center gap-2">
        <svg className="w-5 h-5 text-primary-500" viewBox="0 0 24 24" fill="none">
          <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
        问答记录详情
      </h4>

      <div className="space-y-4">
        {answers.map((answer, idx) => (
          <QuestionCard
            key={idx}
            answer={answer}
            index={idx}
            isExpanded={expandedQuestions.has(idx)}
            onToggle={() => toggleQuestion(idx)}
          />
        ))}
      </div>
    </div>
  );
}

// 问题卡片组件
const CONTROL_ACTION_LABELS: Record<string, string> = {
  SKIP: '已跳过',
  CONTINUE: '讲解后继续',
  END: '用户结束',
};

function QuestionCard({
  answer,
  index,
  isExpanded,
  onToggle
}: {
  answer: AnswerItem;
  index: number;
  isExpanded: boolean;
  onToggle: () => void;
}) {
  const controlLabel = answer.agentAction
    ? CONTROL_ACTION_LABELS[answer.agentAction]
    : undefined;
  return (
      <motion.div
          className="bg-white dark:bg-slate-800 rounded-2xl shadow-sm overflow-hidden"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.1 + index * 0.05 }}
    >
      {/* 问题头部 */}
        <div
            className="px-5 py-4 flex items-center justify-between cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors"
        onClick={onToggle}
      >
        <div className="flex items-center gap-3">
          <span
              className="w-8 h-8 bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-lg flex items-center justify-center text-sm font-semibold">
            {answer.questionIndex + 1}
          </span>
          <span
              className="px-3 py-1 bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 text-xs font-medium rounded-full">
            {answer.category || '综合'}
          </span>
          {answer.score === null ? (
            <span className="font-semibold text-slate-400">未评分{controlLabel ? ` · ${controlLabel}` : ''}</span>
          ) : (
            <span className={`font-semibold ${getScoreColor(answer.score, [80, 60])}`}>
              得分: {answer.score}
            </span>
          )}
        </div>
          <motion.svg
          className="w-5 h-5 text-slate-400"
          animate={{ rotate: isExpanded ? 180 : 0 }}
          transition={{ duration: 0.2 }}
          viewBox="0 0 24 24"
          fill="none"
        >
          <polyline points="6,9 12,15 18,9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </motion.svg>
      </div>

      {/* 问题内容 */}
      <div className="px-5 pb-2">
        <p className="text-slate-800 dark:text-white font-medium leading-relaxed">{answer.question}</p>
      </div>

      {/* 展开内容 */}
      <AnimatePresence>
        {isExpanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3 }}
            className="overflow-hidden"
          >
            <div className="px-5 pb-5 space-y-4">
              {/* 你的回答 */}
              <div className="bg-slate-50 dark:bg-slate-700/50 rounded-xl p-4">
                <p className="text-sm text-slate-500 dark:text-slate-400 mb-2 flex items-center gap-1">
                  <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                    <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  你的回答
                </p>
                <p className={`leading-relaxed ${
                  !answer.userAnswer || answer.userAnswer === '不知道' 
                    ? 'text-red-500 font-medium'
                      : 'text-slate-700 dark:text-slate-300'
                }`}>
                  "{answer.userAnswer || '(未回答)'}"
                </p>
              </div>

              {/* AI 深度评价 */}
              {answer.feedback && (
                <div>
                  <p className="text-sm text-slate-600 dark:text-slate-400 mb-2 flex items-center gap-2 font-medium">
                    <svg className="w-4 h-4 text-primary-500" viewBox="0 0 24 24" fill="none">
                      <path d="M3 3V21H21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      <path d="M18 9L12 15L9 12L3 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                    AI 深度评价
                  </p>
                  <p className="text-slate-700 dark:text-slate-300 leading-relaxed pl-6">{answer.feedback}</p>
                </div>
              )}

              {answer.evaluation && (
                <TurnEvidenceCard
                  evaluation={answer.evaluation}
                  action={answer.agentAction}
                  rationale={answer.decisionRationale}
                />
              )}

              {!answer.evaluation && controlLabel && (
                <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-600 dark:bg-slate-700/40">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-sm font-bold text-slate-700 dark:text-slate-200">{controlLabel}</span>
                    <span className="font-mono text-[11px] text-slate-400">NO SCORE</span>
                  </div>
                  {answer.decisionRationale && (
                    <p className="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">{answer.decisionRationale}</p>
                  )}
                </div>
              )}

              {/* 参考答案 */}
              {answer.referenceAnswer && (
                  <div
                      className="bg-slate-50 dark:bg-slate-700/50 rounded-xl p-4 border border-slate-100 dark:border-slate-600">
                    <p className="text-sm text-slate-600 dark:text-slate-400 mb-3 flex items-center gap-2 font-medium">
                    <svg className="w-4 h-4 text-primary-500" viewBox="0 0 24 24" fill="none">
                      <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2"/>
                      <path d="M9 12H15" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                      <path d="M12 9V15" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                    </svg>
                    参考答案
                  </p>
                    <div
                        className="text-slate-700 dark:text-slate-300 leading-relaxed whitespace-pre-line">{answer.referenceAnswer}</div>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

const DIMENSION_LABELS: Array<[keyof InterviewTurnEvaluation, string]> = [
  ['technicalCorrectness', '技术正确性'],
  ['technicalDepth', '技术深度'],
  ['completeness', '回答完整性'],
  ['scenarioReasoning', '场景推理'],
  ['projectUnderstanding', '项目理解'],
  ['troubleshooting', '排障能力'],
  ['expressionStructure', '表达结构'],
  ['clarity', '清晰度'],
  ['credibility', '可信度'],
  ['jobRelevance', '岗位相关性'],
];

const ACTION_LABELS: Record<string, string> = {
  FOLLOW_UP: '继续追问',
  SWITCH_TOPIC: '切换主题',
  ADJUST_DIFFICULTY: '调整难度',
  END_INTERVIEW: '结束面试',
};

function TurnEvidenceCard({
  evaluation,
  action,
  rationale,
}: {
  evaluation: InterviewTurnEvaluation;
  action?: string | null;
  rationale?: string | null;
}) {
  const dimensions = DIMENSION_LABELS.flatMap(([key, label]) => {
    const value = evaluation[key];
    return typeof value === 'number' ? [{ key, label, value }] : [];
  });

  return (
    <div className="rounded-xl border border-indigo-100 bg-indigo-50/50 p-4 dark:border-indigo-900/50 dark:bg-indigo-950/20">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-bold text-indigo-950 dark:text-indigo-100">Agent 单轮判断</p>
        <div className="flex items-center gap-2">
          {action && (
            <span className="rounded-md bg-indigo-600 px-2.5 py-1 text-[11px] font-bold text-white">
              {ACTION_LABELS[action] || action}
            </span>
          )}
          <span className="font-mono text-[11px] text-indigo-500">置信度 {evaluation.confidence}%</span>
        </div>
      </div>

      {rationale && <p className="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{rationale}</p>}

      {dimensions.length > 0 && (
        <div className="mt-4 grid gap-2 sm:grid-cols-2">
          {dimensions.map(({ key, label, value }) => (
            <div key={String(key)} className="flex items-center gap-3 rounded-lg bg-white/80 px-3 py-2 dark:bg-slate-800/70">
              <span className="w-20 shrink-0 text-xs text-slate-500 dark:text-slate-400">{label}</span>
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                <div className="h-full rounded-full bg-indigo-500" style={{ width: `${value}%` }} />
              </div>
              <span className="w-7 text-right font-mono text-xs font-bold text-slate-700 dark:text-slate-200">{value}</span>
            </div>
          ))}
        </div>
      )}

      {evaluation.evidenceSnippets?.length > 0 && (
        <div className="mt-4 border-l-2 border-indigo-300 pl-3 dark:border-indigo-700">
          <p className="mb-1 text-[11px] font-bold uppercase tracking-wider text-indigo-500">回答原文证据</p>
          {evaluation.evidenceSnippets.map((snippet, index) => (
            <p key={`${snippet}-${index}`} className="text-sm italic leading-6 text-slate-700 dark:text-slate-300">
              “{snippet}”
            </p>
          ))}
        </div>
      )}

      {(evaluation.missingPoints?.length > 0 || evaluation.errors?.length > 0) && (
        <div className="mt-4 flex flex-wrap gap-2">
          {evaluation.missingPoints?.map(item => (
            <span key={`missing-${item}`} className="rounded-md bg-amber-100 px-2 py-1 text-xs text-amber-800 dark:bg-amber-900/30 dark:text-amber-300">
              缺失：{item}
            </span>
          ))}
          {evaluation.errors?.map(item => (
            <span key={`error-${item}`} className="rounded-md bg-rose-100 px-2 py-1 text-xs text-rose-800 dark:bg-rose-900/30 dark:text-rose-300">
              错误：{item}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
