import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Activity,
  AlertCircle,
  Bot,
  BrainCircuit,
  BriefcaseBusiness,
  CalendarDays,
  Check,
  CheckCircle2,
  ChevronRight,
  Circle,
  ClipboardCheck,
  Clock3,
  FileText,
  Gauge,
  ListChecks,
  Loader2,
  PauseCircle,
  Play,
  Quote,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Target,
  Wrench,
  Workflow,
  XCircle,
} from 'lucide-react';
import { agentApi } from '../api/agent';
import { historyApi, type ResumeListItem } from '../api/history';
import { jobApi, type JobItem } from '../api/jobs';
import { getErrorMessage } from '../api/request';
import type {
  AgentPlanStep,
  AgentRun,
  AgentRunStatus,
  BusinessContextArtifact,
  EvidenceCoverageType,
  JobMatchReportArtifact,
  PreparationTask,
  PreparationDecisionArtifact,
  PreparationStrategy,
  RequirementEvidence,
  ResumeImprovementPlanArtifact,
} from '../types/agent';

const DEFAULT_GOAL = '分析所选简历与目标岗位的匹配度，并生成可执行的简历改进计划';

interface AgentTaskPreset {
  jobId?: number;
  resumeId?: number;
  goal?: string;
}

const RUN_STATUS: Record<AgentRunStatus, { label: string; className: string }> = {
  PLANNING: { label: '正在规划', className: 'border-sky-400/30 bg-sky-400/10 text-sky-200' },
  RUNNING: { label: '执行中', className: 'border-cyan-400/30 bg-cyan-400/10 text-cyan-200' },
  WAITING_ASYNC: { label: '等待业务结果', className: 'border-amber-400/30 bg-amber-400/10 text-amber-200' },
  WAITING_APPROVAL: { label: '等待确认', className: 'border-orange-400/30 bg-orange-400/10 text-orange-200' },
  PAUSED: { label: '已暂停', className: 'border-slate-400/30 bg-slate-400/10 text-slate-200' },
  PARTIALLY_COMPLETED: { label: '部分完成', className: 'border-violet-400/30 bg-violet-400/10 text-violet-200' },
  COMPLETED: { label: '执行完成', className: 'border-emerald-400/30 bg-emerald-400/10 text-emerald-200' },
  FAILED: { label: '执行失败', className: 'border-rose-400/30 bg-rose-400/10 text-rose-200' },
  CANCELLED: { label: '已取消', className: 'border-slate-400/30 bg-slate-400/10 text-slate-200' },
};

const STRATEGY_LABEL: Record<PreparationStrategy, string> = {
  RESUME_FIRST: '简历证据优先',
  PROJECT_FIRST: '项目补强优先',
  INTERVIEW_FIRST: '面试训练优先',
  BALANCED: '多方向均衡推进',
};

export default function AgentTaskPage() {
  const location = useLocation();
  const preset = useRef((location.state as AgentTaskPreset | null) ?? {}).current;
  const [jobs, setJobs] = useState<JobItem[]>([]);
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [jobId, setJobId] = useState('');
  const [resumeId, setResumeId] = useState('');
  const [goal, setGoal] = useState(DEFAULT_GOAL);
  const [run, setRun] = useState<AgentRun | null>(null);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [starting, setStarting] = useState(false);
  const [resuming, setResuming] = useState(false);
  const [error, setError] = useState('');
  const resumeInFlight = useRef(false);

  const availableJobs = useMemo(
    () => jobs.filter(job => job.status !== 'ARCHIVED'),
    [jobs]
  );
  const availableResumes = useMemo(
    () => resumes.filter(resume => resume.analyzeStatus === 'COMPLETED' || resume.latestScore !== undefined),
    [resumes]
  );
  const selectedJob = availableJobs.find(job => job.id === Number(jobId));
  const selectedResume = availableResumes.find(resume => resume.id === Number(resumeId));

  useEffect(() => {
    let cancelled = false;
    const loadOptions = async () => {
      setLoadingOptions(true);
      try {
        const [jobList, resumeList] = await Promise.all([
          jobApi.list(),
          historyApi.getResumes(),
        ]);
        if (cancelled) return;
        setJobs(jobList);
        setResumes(resumeList);
        const firstJob = jobList.find(job => job.id === preset.jobId)
          ?? jobList.find(job => job.status !== 'ARCHIVED');
        const firstResume = resumeList.find(resume => resume.id === preset.resumeId)
          ?? resumeList.find(
          resume => resume.analyzeStatus === 'COMPLETED' || resume.latestScore !== undefined
        );
        setJobId(firstJob?.id.toString() ?? '');
        setResumeId(firstResume?.id.toString() ?? '');
        if (preset.goal?.trim()) setGoal(preset.goal.trim());
      } catch (err) {
        if (!cancelled) setError(getErrorMessage(err));
      } finally {
        if (!cancelled) setLoadingOptions(false);
      }
    };
    void loadOptions();
    return () => {
      cancelled = true;
    };
  }, []);

  const resumeRun = useCallback(async (runId: string) => {
    if (resumeInFlight.current) return;
    resumeInFlight.current = true;
    setResuming(true);
    try {
      const nextRun = await agentApi.resumeRun(runId);
      setRun(nextRun);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      resumeInFlight.current = false;
      setResuming(false);
    }
  }, []);

  useEffect(() => {
    if (!run || run.status !== 'WAITING_ASYNC') return;
    const timer = window.setTimeout(() => {
      void resumeRun(run.id);
    }, 3000);
    return () => window.clearTimeout(timer);
  }, [run, resumeRun]);

  const handleStart = async () => {
    if (!jobId || !resumeId || goal.trim().length < 10) return;
    setStarting(true);
    setError('');
    setRun(null);
    try {
      const created = await agentApi.createRun({
        goal: goal.trim(),
        constraints: {
          jobId: Number(jobId),
          resumeId: Number(resumeId),
        },
      });
      setRun(created);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setStarting(false);
    }
  };

  const canStart = Boolean(jobId && resumeId && goal.trim().length >= 10 && !starting);
  const businessContext = findArtifact<BusinessContextArtifact>(run, 'business_context');
  const matchReport = findArtifact<JobMatchReportArtifact>(run, 'job_match_report');
  const decision = findArtifact<PreparationDecisionArtifact>(run, 'preparation_decision');
  const improvementPlan = findArtifact<ResumeImprovementPlanArtifact>(run, 'resume_improvement_plan');

  return (
    <main className="mx-auto w-full max-w-[1500px] space-y-6 px-5 py-6 lg:px-8 lg:py-8">
      <AgentHero run={run} />

      {error && (
        <div className="flex items-start gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <div className="flex-1">{error}</div>
          {run?.status === 'WAITING_ASYNC' && (
            <button
              type="button"
              onClick={() => void resumeRun(run.id)}
              disabled={resuming}
              className="font-semibold underline underline-offset-4 disabled:opacity-50"
            >
              重新检查
            </button>
          )}
        </div>
      )}

      <section className="grid gap-6 xl:grid-cols-[minmax(320px,0.78fr)_minmax(520px,1.45fr)]">
        <TaskConfiguration
          jobs={availableJobs}
          resumes={availableResumes}
          jobId={jobId}
          resumeId={resumeId}
          goal={goal}
          loading={loadingOptions}
          starting={starting}
          canStart={canStart}
          selectedJob={selectedJob}
          selectedResume={selectedResume}
          onJobChange={setJobId}
          onResumeChange={setResumeId}
          onGoalChange={setGoal}
          onStart={() => void handleStart()}
        />

        <ExecutionPanel
          run={run}
          starting={starting}
          resuming={resuming}
          businessContext={businessContext}
          onResume={() => run && void resumeRun(run.id)}
        />
      </section>

      {(matchReport || decision || improvementPlan) && (
        <motion.section
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="space-y-6"
        >
          {matchReport && <MatchReportCard report={matchReport} />}
          {decision && <PreparationDecisionCard decision={decision} />}
          {improvementPlan && <ImprovementPlanCard plan={improvementPlan} />}
        </motion.section>
      )}
    </main>
  );
}

function AgentHero({ run }: { run: AgentRun | null }) {
  return (
    <motion.section
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="relative overflow-hidden rounded-[28px] border border-slate-700/70 bg-slate-950 px-6 py-7 text-white shadow-2xl shadow-slate-900/20 lg:px-9"
    >
      <div className="absolute inset-0 opacity-30 [background-image:linear-gradient(rgba(148,163,184,.12)_1px,transparent_1px),linear-gradient(90deg,rgba(148,163,184,.12)_1px,transparent_1px)] [background-size:32px_32px]" />
      <div className="absolute -right-24 -top-36 h-80 w-80 rounded-full bg-cyan-400/15 blur-3xl" />
      <div className="relative grid items-center gap-7 lg:grid-cols-[1.2fr_0.8fr]">
        <div>
          <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-cyan-300/20 bg-cyan-300/10 px-3 py-1 text-xs font-semibold tracking-[0.18em] text-cyan-200">
            <Activity className="h-3.5 w-3.5" /> BUSINESS AGENT CONTROL
          </div>
          <h1 className="max-w-3xl text-3xl font-bold leading-tight sm:text-4xl">
            Agent 执行详情
          </h1>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300 sm:text-base">
            从岗位工作区发起后，在这里查看 Agent 如何读取业务数据、等待异步结果并完成准备规划。
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            {['动态模型', '业务工具调用', 'Checkpoint 恢复'].map(label => (
              <span key={label} className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-slate-300">
                {label}
              </span>
            ))}
            <Link to="/jobs" className="inline-flex items-center gap-1 rounded-full border border-lime-300/20 bg-lime-300/10 px-3 py-1.5 text-xs font-semibold text-lime-200">
              返回目标岗位 <ChevronRight className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>

        <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-4 backdrop-blur-xl">
          <div className="mb-4 flex items-center justify-between text-xs text-slate-400">
            <span>实时执行链路</span>
            <span className="flex items-center gap-1.5 text-emerald-300">
              <span className="h-2 w-2 animate-pulse rounded-full bg-emerald-400" /> READY
            </span>
          </div>
          <div className="flex items-center gap-2">
            <FlowNode icon={FileText} label="简历" />
            <ChevronRight className="h-4 w-4 shrink-0 text-slate-600" />
            <FlowNode icon={Bot} label="Agent" active={Boolean(run)} />
            <ChevronRight className="h-4 w-4 shrink-0 text-slate-600" />
            <FlowNode icon={Target} label="结果" completed={run?.status === 'COMPLETED'} />
          </div>
        </div>
      </div>
    </motion.section>
  );
}

interface TaskConfigurationProps {
  jobs: JobItem[];
  resumes: ResumeListItem[];
  jobId: string;
  resumeId: string;
  goal: string;
  loading: boolean;
  starting: boolean;
  canStart: boolean;
  selectedJob?: JobItem;
  selectedResume?: ResumeListItem;
  onJobChange: (value: string) => void;
  onResumeChange: (value: string) => void;
  onGoalChange: (value: string) => void;
  onStart: () => void;
}

function TaskConfiguration(props: TaskConfigurationProps) {
  return (
    <motion.div
      initial={{ opacity: 0, x: -12 }}
      animate={{ opacity: 1, x: 0 }}
      className="dark-card h-fit p-5 lg:p-6"
    >
      <div className="mb-6 flex items-center gap-3">
        <div className="rounded-xl bg-slate-950 p-2.5 text-cyan-300 dark:bg-cyan-400/10">
          <Workflow className="h-5 w-5" />
        </div>
        <div>
          <h2 className="text-lg text-slate-900 dark:text-white">执行参数</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400">来自岗位工作区，可在启动前调整</p>
        </div>
      </div>

      {props.loading ? (
        <div className="flex min-h-72 items-center justify-center text-slate-400">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" /> 正在读取业务数据
        </div>
      ) : (
        <div className="space-y-5">
          <SelectField
            label="目标岗位"
            icon={BriefcaseBusiness}
            value={props.jobId}
            onChange={props.onJobChange}
            emptyText="暂无可用岗位"
            disabled={!props.jobs.length}
          >
            {props.jobs.map(job => (
              <option key={job.id} value={job.id}>
                {job.title}{job.company ? ` · ${job.company}` : ''}
              </option>
            ))}
          </SelectField>

          <SelectField
            label="分析简历"
            icon={FileText}
            value={props.resumeId}
            onChange={props.onResumeChange}
            emptyText="暂无已分析简历"
            disabled={!props.resumes.length}
          >
            {props.resumes.map(resume => (
              <option key={resume.id} value={resume.id}>
                {resume.filename}{resume.latestScore !== undefined ? ` · ${resume.latestScore} 分` : ''}
              </option>
            ))}
          </SelectField>

          <label className="block">
            <span className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
              <Target className="h-4 w-4 text-cyan-600 dark:text-cyan-400" /> 执行目标
            </span>
            <textarea
              value={props.goal}
              onChange={event => props.onGoalChange(event.target.value)}
              rows={4}
              maxLength={4000}
              className="dark-input w-full resize-none rounded-xl px-3.5 py-3 text-sm leading-6 outline-none"
              placeholder="描述希望 Agent 完成的业务目标"
            />
            <span className="mt-1 block text-right text-[11px] text-slate-400">{props.goal.trim().length} / 4000</span>
          </label>

          {(props.selectedJob || props.selectedResume) && (
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs dark:border-slate-700 dark:bg-slate-900/50">
              <p className="mb-2 font-semibold text-slate-700 dark:text-slate-200">本次 Agent 上下文</p>
              <p className="truncate text-slate-500 dark:text-slate-400">岗位：{props.selectedJob?.title ?? '未选择'}</p>
              <p className="mt-1 truncate text-slate-500 dark:text-slate-400">简历：{props.selectedResume?.filename ?? '未选择'}</p>
            </div>
          )}

          {(!props.jobs.length || !props.resumes.length) && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-800 dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-200">
              {!props.jobs.length && <Link className="font-semibold underline" to="/jobs">先添加一个目标岗位</Link>}
              {!props.jobs.length && !props.resumes.length && <span>，并</span>}
              {!props.resumes.length && <Link className="font-semibold underline" to="/upload">上传并分析一份简历</Link>}
              <span>，然后再启动 Agent。</span>
            </div>
          )}

          <button
            type="button"
            onClick={props.onStart}
            disabled={!props.canStart}
            className="group flex w-full items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 py-3.5 text-sm font-semibold text-white shadow-lg shadow-slate-900/15 transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-45 dark:bg-cyan-400 dark:text-slate-950 dark:hover:bg-cyan-300"
          >
            {props.starting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4 fill-current" />}
            {props.starting ? 'Agent 正在规划...' : '启动业务 Agent'}
          </button>
        </div>
      )}
    </motion.div>
  );
}

function ExecutionPanel({
  run,
  starting,
  resuming,
  businessContext,
  onResume,
}: {
  run: AgentRun | null;
  starting: boolean;
  resuming: boolean;
  businessContext?: BusinessContextArtifact;
  onResume: () => void;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, x: 12 }}
      animate={{ opacity: 1, x: 0 }}
      className="dark-card min-h-[520px] overflow-hidden"
    >
      <div className="flex items-center justify-between border-b border-slate-200/70 px-5 py-4 dark:border-slate-700/70 lg:px-6">
        <div className="flex items-center gap-3">
          <div className="rounded-xl bg-cyan-50 p-2.5 text-cyan-700 dark:bg-cyan-400/10 dark:text-cyan-300">
            <Activity className="h-5 w-5" />
          </div>
          <div>
            <h2 className="text-lg text-slate-900 dark:text-white">执行轨迹</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">业务步骤、等待点与执行结果</p>
          </div>
        </div>
        {run && <RunStatusBadge status={run.status} />}
      </div>

      {starting ? (
        <div className="flex min-h-[430px] flex-col items-center justify-center px-6 text-center">
          <div className="relative mb-6">
            <div className="absolute inset-0 animate-ping rounded-full bg-cyan-400/20" />
            <div className="relative rounded-2xl bg-slate-950 p-4 text-cyan-300 dark:bg-cyan-400/10">
              <Bot className="h-8 w-8" />
            </div>
          </div>
          <h3 className="text-lg text-slate-800 dark:text-white">模型正在拆解业务目标</h3>
          <p className="mt-2 max-w-sm text-sm leading-6 text-slate-500 dark:text-slate-400">规划完成后，Agent 会直接读取简历和岗位并启动匹配任务。</p>
        </div>
      ) : !run ? (
        <EmptyExecution />
      ) : (
        <div className="p-5 lg:p-6">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 px-4 py-3 dark:bg-slate-900/50">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Run ID</p>
              <p className="mt-1 font-mono text-xs text-slate-600 dark:text-slate-300">{run.id}</p>
            </div>
            <div className="text-right text-xs text-slate-500 dark:text-slate-400">
              <p>业务轮询 {run.poll_count} 次</p>
              {businessContext && <p className="mt-1">{businessContext.resumeFilename} → {businessContext.jobTitle}</p>}
            </div>
          </div>

          <div className="space-y-2">
            {run.plan.map((step, index) => (
              <PlanStepRow key={step.id} step={step} index={index} last={index === run.plan.length - 1} />
            ))}
          </div>

          {run.status === 'WAITING_ASYNC' && (
            <div className="mt-5 flex items-center gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 dark:border-amber-900/50 dark:bg-amber-950/25">
              <div className="rounded-lg bg-amber-100 p-2 text-amber-700 dark:bg-amber-900/50 dark:text-amber-300">
                {resuming ? <RefreshCw className="h-5 w-5 animate-spin" /> : <Clock3 className="h-5 w-5" />}
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-amber-900 dark:text-amber-100">Java 异步任务执行中</p>
                <p className="mt-0.5 text-xs text-amber-700 dark:text-amber-300">页面会自动恢复 Agent，无需重复创建任务。</p>
              </div>
              <button
                type="button"
                onClick={onResume}
                disabled={resuming}
                className="rounded-lg border border-amber-300 px-3 py-2 text-xs font-semibold text-amber-800 transition hover:bg-amber-100 disabled:opacity-50 dark:border-amber-700 dark:text-amber-200 dark:hover:bg-amber-900/40"
              >
                立即检查
              </button>
            </div>
          )}

          {run.errors.length > 0 && (
            <div className="mt-5 space-y-2">
              {run.errors.map((item, index) => (
                <div key={`${item.stepId}-${index}`} className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-200">
                  <span className="font-semibold">{item.stepId}</span> · {item.message}
                </div>
              ))}
            </div>
          )}

          {run.status === 'COMPLETED' && (
            <div className="mt-5 flex items-center gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/25 dark:text-emerald-200">
              <CheckCircle2 className="h-5 w-5" />
              <div>
                <p className="text-sm font-semibold">Agent 已完成全部业务步骤</p>
                <p className="mt-0.5 text-xs opacity-80">
                  {run.improvement_plan_id
                    ? '匹配报告与改进计划已持久化到 Java 业务库。'
                    : 'Agent 根据目标保留匹配报告，并跳过了计划写入。'}
                </p>
              </div>
            </div>
          )}
        </div>
      )}
    </motion.div>
  );
}

function MatchReportCard({ report }: { report: JobMatchReportArtifact }) {
  return (
    <div className="dark-card overflow-hidden">
      <div className="grid gap-6 border-b border-slate-200/70 p-6 dark:border-slate-700/70 lg:grid-cols-[1fr_auto]">
        <div>
          <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-cyan-600 dark:text-cyan-400">
            <Gauge className="h-4 w-4" /> Job Match Evidence
          </div>
          <h2 className="text-2xl text-slate-900 dark:text-white">岗位匹配报告</h2>
          <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-600 dark:text-slate-300">{report.summary}</p>
        </div>
        <div className="flex h-24 w-24 items-center justify-center rounded-full border-[7px] border-emerald-100 bg-emerald-50 text-center dark:border-emerald-900/50 dark:bg-emerald-950/30">
          <div><strong className="block text-3xl text-emerald-700 dark:text-emerald-300">{report.overallScore}</strong><span className="text-[10px] text-emerald-600 dark:text-emerald-400">综合匹配</span></div>
        </div>
      </div>
      <div className="grid grid-cols-3 border-b border-slate-200/70 dark:border-slate-700/70">
        <ScoreCell label="技能覆盖" value={report.skillScore} />
        <ScoreCell label="项目证据" value={report.projectScore} />
        <ScoreCell label="关键词" value={report.keywordScore} />
      </div>
      <div className="grid gap-4 p-6 lg:grid-cols-3">
        <ResultList title="已匹配证据" items={report.matchedHighlights} tone="success" />
        <ResultList title="关键差距" items={report.gaps} tone="warning" />
        <ResultList title="建议动作" items={report.actionItems} tone="action" />
      </div>
      {(report.evidenceMappings?.length ?? 0) > 0 && (
        <EvidenceMatrix mappings={report.evidenceMappings} />
      )}
    </div>
  );
}

const COVERAGE_META: Record<EvidenceCoverageType, { label: string; className: string }> = {
  SUPPORTED: {
    label: '充分支持',
    className: 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-300',
  },
  EXPRESSION_GAP: {
    label: '表达缺失',
    className: 'border-sky-200 bg-sky-50 text-sky-700 dark:border-sky-900 dark:bg-sky-950/40 dark:text-sky-300',
  },
  EVIDENCE_GAP: {
    label: '证据不足',
    className: 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-300',
  },
  CAPABILITY_GAP: {
    label: '能力缺失',
    className: 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-300',
  },
};

function EvidenceMatrix({ mappings }: { mappings: RequirementEvidence[] }) {
  return (
    <div className="border-t border-slate-200/70 bg-slate-50/70 px-6 py-6 dark:border-slate-700/70 dark:bg-slate-950/30">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[10px] uppercase tracking-[0.2em] text-cyan-600 dark:text-cyan-400">
            Requirement × Evidence
          </p>
          <h3 className="mt-1 text-lg font-semibold text-slate-900 dark:text-white">JD—简历证据矩阵</h3>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">每项判断都保留 JD 与简历原文，不用模型常识代替证据。</p>
        </div>
        <span className="rounded-full border border-slate-200 bg-white px-3 py-1.5 font-mono text-[11px] text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
          {mappings.length} REQUIREMENTS
        </span>
      </div>

      <div className="space-y-3">
        {mappings.map(mapping => (
          <EvidenceRow key={mapping.requirement.id} mapping={mapping} />
        ))}
      </div>
    </div>
  );
}

function EvidenceRow({ mapping }: { mapping: RequirementEvidence }) {
  const coverage = COVERAGE_META[mapping.coverageType];
  return (
    <div className="grid overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900 lg:grid-cols-[0.8fr_1.35fr_0.85fr]">
      <div className="border-b border-slate-200 p-4 dark:border-slate-700 lg:border-b-0 lg:border-r">
        <div className="flex items-center justify-between gap-3">
          <span className="font-mono text-[10px] font-bold text-cyan-700 dark:text-cyan-300">{mapping.requirement.id}</span>
          <span className="text-[10px] font-bold text-slate-400">{mapping.requirement.importance}</span>
        </div>
        <p className="mt-2 text-sm font-semibold leading-5 text-slate-900 dark:text-white">{mapping.requirement.description}</p>
        <p className="mt-2 text-[11px] text-slate-400">{mapping.requirement.category}</p>
      </div>

      <div className="border-b border-slate-200 p-4 dark:border-slate-700 lg:border-b-0 lg:border-r">
        <EvidenceQuote label="JD" text={mapping.requirement.sourceQuote} />
        <div className="mt-3 space-y-2">
          {mapping.resumeEvidence.length > 0 ? mapping.resumeEvidence.map((evidence, index) => (
            <EvidenceQuote
              key={`${mapping.requirement.id}-resume-${index}`}
              label={evidence.sourceLocation || '简历'}
              text={evidence.quote}
            />
          )) : (
            <p className="rounded-xl border border-dashed border-rose-200 bg-rose-50/60 px-3 py-2 text-xs text-rose-600 dark:border-rose-900/60 dark:bg-rose-950/20 dark:text-rose-300">
              简历中未找到可引用证据
            </p>
          )}
        </div>
      </div>

      <div className="p-4">
        <div className="flex items-center justify-between gap-3">
          <span className={`rounded-full border px-2.5 py-1 text-[11px] font-bold ${coverage.className}`}>{coverage.label}</span>
          <span className="font-mono text-[11px] text-slate-400">{mapping.confidence}%</span>
        </div>
        <p className="mt-3 text-xs leading-5 text-slate-600 dark:text-slate-300">{mapping.reasoning}</p>
        <div className="mt-3 rounded-xl bg-slate-950 px-3 py-2.5 text-xs leading-5 text-slate-200 dark:bg-black/30">
          {mapping.recommendedAction}
        </div>
      </div>
    </div>
  );
}

function EvidenceQuote({ label, text }: { label: string; text: string }) {
  return (
    <div className="flex gap-2.5">
      <Quote className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-300 dark:text-slate-600" />
      <div>
        <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">{label}</p>
        <p className="mt-0.5 text-xs leading-5 text-slate-600 dark:text-slate-300">{text}</p>
      </div>
    </div>
  );
}

function PreparationDecisionCard({ decision }: { decision: PreparationDecisionArtifact }) {
  return (
    <div className="relative overflow-hidden rounded-[24px] border border-amber-200 bg-[#fffaf0] shadow-lg shadow-amber-900/5 dark:border-amber-900/60 dark:bg-[#17130c]">
      <div className="absolute inset-y-0 left-0 w-1.5 bg-amber-400" />
      <div className="grid gap-5 border-b border-amber-200/80 px-6 py-6 dark:border-amber-900/50 lg:grid-cols-[1fr_auto]">
        <div>
          <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-amber-700 dark:text-amber-300">
            <BrainCircuit className="h-4 w-4" /> Agent Decision Gate
          </div>
          <h2 className="text-2xl text-slate-950 dark:text-white">准备策略决策</h2>
          <p className="mt-3 max-w-4xl text-sm leading-6 text-slate-600 dark:text-slate-300">
            {decision.rationale}
          </p>
        </div>
        <div className="flex flex-col items-start gap-2 lg:items-end">
          <span className="rounded-full border border-amber-300 bg-amber-100 px-3 py-1.5 text-xs font-bold text-amber-900 dark:border-amber-700 dark:bg-amber-950/60 dark:text-amber-200">
            {STRATEGY_LABEL[decision.strategy]}
          </span>
          <span className="inline-flex items-center gap-1.5 font-mono text-[11px] text-slate-500 dark:text-slate-400">
            <Wrench className="h-3.5 w-3.5" />
            {decision.selectedTool ?? '不调用写业务 Tool'}
          </span>
        </div>
      </div>

      <div className="grid gap-px bg-amber-200/70 dark:bg-amber-900/40 lg:grid-cols-3">
        <DecisionEvidenceColumn
          code="GAP"
          title="优先处理的缺口"
          items={decision.prioritizedGaps}
        />
        <DecisionEvidenceColumn
          code="EVD"
          title="决策依据"
          items={decision.supportingEvidence}
        />
        <DecisionEvidenceColumn
          code="INT"
          title="面试验证重点"
          items={decision.interviewFocus}
        />
      </div>
    </div>
  );
}

function DecisionEvidenceColumn({
  code,
  title,
  items,
}: {
  code: string;
  title: string;
  items: string[];
}) {
  return (
    <div className="bg-[#fffdf8] p-5 dark:bg-[#17130c]">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-slate-900 dark:text-white">{title}</h3>
        <span className="font-mono text-[10px] tracking-widest text-amber-700 dark:text-amber-400">{code}</span>
      </div>
      <ul className="space-y-3">
        {items.map((item, index) => (
          <li key={`${code}-${index}`} className="flex gap-2.5 text-xs leading-5 text-slate-600 dark:text-slate-300">
            <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500" />
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function ImprovementPlanCard({ plan }: { plan: ResumeImprovementPlanArtifact }) {
  return (
    <div className="overflow-hidden rounded-[24px] border border-slate-800 bg-slate-950 text-white shadow-xl shadow-slate-900/15">
      <div className="grid gap-6 border-b border-white/10 p-6 lg:grid-cols-[1fr_auto]">
        <div>
          <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-cyan-300">
            <Sparkles className="h-4 w-4" /> Actionable Upgrade Plan
          </div>
          <h2 className="text-2xl">简历改进计划</h2>
          <p className="mt-3 max-w-4xl text-sm leading-6 text-slate-300">{plan.summary}</p>
        </div>
        <div className="rounded-2xl border border-cyan-300/20 bg-cyan-300/10 px-5 py-4 text-center">
          <strong className="block text-3xl text-cyan-200">{plan.readinessScore}</strong>
          <span className="text-[11px] text-cyan-300">岗位准备度</span>
        </div>
      </div>
      <div className="grid gap-px bg-white/10 lg:grid-cols-2">
        <DarkResultList title="优先修复" items={plan.priorityFixes} indexLabel="P" />
        <DarkResultList title="简历改写" items={plan.resumeRewriteBullets} indexLabel="R" />
        <DarkResultList title="项目升级" items={plan.projectUpgradeTasks} indexLabel="B" />
        <DarkResultList title="面试训练" items={plan.interviewPracticeTasks} indexLabel="I" />
      </div>
      {(plan.preparationTasks?.length ?? 0) > 0 && (
        <PreparationTaskQueue tasks={plan.preparationTasks} />
      )}
    </div>
  );
}

function PreparationTaskQueue({ tasks }: { tasks: PreparationTask[] }) {
  return (
    <div className="border-t border-white/10 bg-[#0a101b] p-6">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[10px] uppercase tracking-[0.2em] text-cyan-400">Execution Queue</p>
          <h3 className="mt-1 text-lg font-semibold text-white">岗位准备任务队列</h3>
          <p className="mt-1 text-xs text-slate-400">按优先级执行，并用明确方式验证是否真正补齐。</p>
        </div>
        <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 font-mono text-[11px] text-slate-300">
          {tasks.length} TASKS
        </span>
      </div>
      <div className="grid gap-3 lg:grid-cols-2">
        {tasks.map(task => <PreparationTaskCard key={task.id} task={task} />)}
      </div>
    </div>
  );
}

function PreparationTaskCard({ task }: { task: PreparationTask }) {
  const priorityClass = task.priority === 'P0'
    ? 'bg-rose-400 text-slate-950'
    : task.priority === 'P1'
      ? 'bg-amber-300 text-slate-950'
      : 'bg-slate-700 text-slate-200';
  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.045] p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className={`rounded-md px-2 py-1 font-mono text-[10px] font-black ${priorityClass}`}>{task.priority}</span>
          <span className="font-mono text-[10px] text-cyan-300">{task.id}</span>
        </div>
        <span className="flex items-center gap-1 text-[11px] text-slate-400">
          <CalendarDays className="h-3.5 w-3.5" /> {task.suggestedDays} 天
        </span>
      </div>
      <p className="mt-3 text-sm font-semibold leading-5 text-white">{task.title}</p>
      <div className="mt-3 flex gap-2 text-xs leading-5 text-slate-300">
        <ClipboardCheck className="mt-0.5 h-4 w-4 shrink-0 text-emerald-300" />
        <span>{task.verificationMethod}</span>
      </div>
      <div className="mt-3 flex flex-wrap gap-1.5">
        <span className="rounded-full border border-white/10 px-2 py-1 text-[10px] text-slate-400">{task.category}</span>
        {task.relatedRequirementIds.map(requirementId => (
          <span key={requirementId} className="rounded-full bg-cyan-400/10 px-2 py-1 font-mono text-[10px] text-cyan-300">{requirementId}</span>
        ))}
      </div>
    </div>
  );
}

function EmptyExecution() {
  return (
    <div className="flex min-h-[430px] flex-col items-center justify-center px-6 text-center">
      <div className="mb-5 grid h-20 w-20 place-items-center rounded-3xl border border-dashed border-slate-300 bg-slate-50 text-slate-400 dark:border-slate-700 dark:bg-slate-900/50">
        <Workflow className="h-9 w-9" />
      </div>
      <h3 className="text-lg text-slate-800 dark:text-white">等待启动业务任务</h3>
      <p className="mt-2 max-w-md text-sm leading-6 text-slate-500 dark:text-slate-400">这里展示的是 Agent 的真实业务执行轨迹，不是模型对话记录。</p>
      <div className="mt-6 flex items-center gap-2 text-xs text-slate-400">
        <span>读取上下文</span><ChevronRight className="h-3.5 w-3.5" /><span>调用业务</span><ChevronRight className="h-3.5 w-3.5" /><span>沉淀结果</span>
      </div>
    </div>
  );
}

function PlanStepRow({ step, index, last }: { step: AgentPlanStep; index: number; last: boolean }) {
  const icon = step.status === 'COMPLETED'
    ? <Check className="h-4 w-4" />
    : step.status === 'IN_PROGRESS'
      ? <RefreshCw className="h-4 w-4 animate-spin" />
      : step.status === 'FAILED'
        ? <XCircle className="h-4 w-4" />
        : step.status === 'SKIPPED'
          ? <PauseCircle className="h-4 w-4" />
          : <Circle className="h-3.5 w-3.5" />;
  const activeClass = step.status === 'COMPLETED'
    ? 'bg-emerald-500 text-white'
    : step.status === 'IN_PROGRESS'
      ? 'bg-cyan-500 text-white'
      : step.status === 'FAILED'
        ? 'bg-rose-500 text-white'
        : 'border border-slate-300 bg-white text-slate-400 dark:border-slate-600 dark:bg-slate-800';

  return (
    <div className="relative flex gap-3 pb-4 last:pb-0">
      {!last && <div className="absolute left-[15px] top-8 h-[calc(100%-1rem)] w-px bg-slate-200 dark:bg-slate-700" />}
      <div className={`relative z-10 grid h-8 w-8 shrink-0 place-items-center rounded-full ${activeClass}`}>{icon}</div>
      <div className="min-w-0 flex-1 rounded-xl border border-slate-200/70 bg-white px-3.5 py-3 dark:border-slate-700/70 dark:bg-slate-900/40">
        <div className="flex items-center justify-between gap-3">
          <p className="text-sm font-medium text-slate-700 dark:text-slate-200">{step.title}</p>
          <span className="shrink-0 font-mono text-[10px] text-slate-400">STEP {String(index + 1).padStart(2, '0')}</span>
        </div>
      </div>
    </div>
  );
}

function SelectField({
  label,
  icon: Icon,
  value,
  onChange,
  emptyText,
  disabled,
  children,
}: {
  label: string;
  icon: typeof FileText;
  value: string;
  onChange: (value: string) => void;
  emptyText: string;
  disabled: boolean;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
        <Icon className="h-4 w-4 text-cyan-600 dark:text-cyan-400" /> {label}
      </span>
      <select
        value={value}
        onChange={event => onChange(event.target.value)}
        disabled={disabled}
        className="dark-input w-full rounded-xl px-3.5 py-3 text-sm outline-none"
      >
        {!value && <option value="">{emptyText}</option>}
        {children}
      </select>
    </label>
  );
}

function FlowNode({ icon: Icon, label, active, completed }: { icon: typeof FileText; label: string; active?: boolean; completed?: boolean }) {
  const className = completed
    ? 'border-emerald-300/30 bg-emerald-300/10 text-emerald-200'
    : active
      ? 'border-cyan-300/40 bg-cyan-300/15 text-cyan-100'
      : 'border-white/10 bg-white/5 text-slate-300';
  return (
    <div className={`flex min-w-0 flex-1 flex-col items-center gap-2 rounded-xl border px-2 py-3 text-xs ${className}`}>
      <Icon className="h-5 w-5" /><span>{label}</span>
    </div>
  );
}

function RunStatusBadge({ status }: { status: AgentRunStatus }) {
  const meta = RUN_STATUS[status];
  return <span className={`rounded-full border px-3 py-1.5 text-xs font-semibold ${meta.className}`}>{meta.label}</span>;
}

function ScoreCell({ label, value }: { label: string; value: number }) {
  return (
    <div className="border-r border-slate-200/70 px-4 py-4 text-center last:border-r-0 dark:border-slate-700/70">
      <strong className="block text-xl text-slate-900 dark:text-white">{value}</strong>
      <span className="text-xs text-slate-500 dark:text-slate-400">{label}</span>
    </div>
  );
}

function ResultList({ title, items, tone }: { title: string; items: string[]; tone: 'success' | 'warning' | 'action' }) {
  const tones = {
    success: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/30 dark:text-emerald-300',
    warning: 'bg-amber-50 text-amber-700 dark:bg-amber-950/30 dark:text-amber-300',
    action: 'bg-cyan-50 text-cyan-700 dark:bg-cyan-950/30 dark:text-cyan-300',
  };
  return (
    <div className="rounded-2xl border border-slate-200/70 p-4 dark:border-slate-700/70">
      <h3 className="mb-3 flex items-center gap-2 text-sm text-slate-800 dark:text-white"><ListChecks className="h-4 w-4" />{title}</h3>
      <ul className="space-y-2.5">
        {items.map((item, index) => (
          <li key={`${title}-${index}`} className="flex gap-2 text-xs leading-5 text-slate-600 dark:text-slate-300">
            <span className={`mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full text-[10px] font-bold ${tones[tone]}`}>{index + 1}</span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function DarkResultList({ title, items, indexLabel }: { title: string; items: string[]; indexLabel: string }) {
  return (
    <div className="bg-slate-950 p-6">
      <h3 className="mb-4 flex items-center gap-2 text-sm text-white"><ShieldCheck className="h-4 w-4 text-cyan-300" />{title}</h3>
      <ul className="space-y-3">
        {items.map((item, index) => (
          <li key={`${title}-${index}`} className="flex gap-3 text-xs leading-5 text-slate-300">
            <span className="mt-0.5 font-mono text-[10px] text-cyan-400">{indexLabel}{String(index + 1).padStart(2, '0')}</span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function findArtifact<T>(run: AgentRun | null, type: string): T | undefined {
  return run?.artifacts.find(artifact => artifact.type === type) as T | undefined;
}
