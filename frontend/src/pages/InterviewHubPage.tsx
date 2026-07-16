import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowRight,
  BriefcaseBusiness,
  Building2,
  Check,
  ChevronRight,
  Clock3,
  Crosshair,
  FileStack,
  FileText,
  History,
  Loader2,
  MapPin,
  Plus,
  RefreshCw,
  Target,
  Zap,
} from 'lucide-react';
import { interviewApi, type TextSessionMeta } from '../api/interview';
import { jobApi, type JobItem } from '../api/jobs';
import { jobMatchApi, type JobMatchReport } from '../api/jobMatches';
import { type SkillDTO } from '../api/skill';
import {
  CUSTOM_SKILL_ID,
  DIFFICULTY_OPTIONS,
  useInterviewConfig,
} from '../hooks/useInterviewConfig';
import { formatDateTime } from '../utils/date';
import { getTemplateName } from '../utils/interview';
import { getScoreTextColor } from '../utils/score';
import { getSkillIcon } from '../utils/skillIcons';

type InterviewSource = 'JOB' | 'PRACTICE';

interface RecentInterviewItem {
  id: string;
  title: string;
  status: string;
  evaluateStatus?: string | null;
  overallScore: number | null;
  createdAt: string;
}

const STRATEGIES = [
  ['GENERAL', '综合摸底', '知识、项目与场景均衡覆盖'],
  ['RESUME_DEFENSE', '简历深挖', '验证项目证据和技术取舍'],
  ['FOCUS_DRILL', '专项强化', '围绕一个薄弱方向连续训练'],
] as const;

export default function InterviewHubPage() {
  const navigate = useNavigate();
  const config = useInterviewConfig({ autoLoad: false });
  const [source, setSource] = useState<InterviewSource>('JOB');
  const [jobs, setJobs] = useState<JobItem[]>([]);
  const [reports, setReports] = useState<JobMatchReport[]>([]);
  const [selectedJobId, setSelectedJobId] = useState<number>();
  const [recentInterviews, setRecentInterviews] = useState<RecentInterviewItem[]>([]);
  const [loading, setLoading] = useState(true);

  const activeJobs = useMemo(
    () => jobs.filter(job => job.status !== 'ARCHIVED'),
    [jobs],
  );
  const selectedJob = activeJobs.find(job => job.id === selectedJobId);
  const selectedReport = reports.find(report => (
    report.jobId === selectedJobId
      && (!config.resumeId || report.resumeId === config.resumeId)
  ));

  const loadRecentInterviews = useCallback((
    sessions: TextSessionMeta[],
    skills: SkillDTO[],
    loadedJobs: JobItem[],
  ) => {
    const items = sessions
      .map(session => ({
        id: session.sessionId,
        title: session.jobId
          ? loadedJobs.find(job => job.id === session.jobId)?.title ?? '岗位定向面试'
          : getTemplateName(session.skillId, skills),
        status: session.status,
        evaluateStatus: session.evaluateStatus,
        overallScore: session.overallScore,
        createdAt: session.createdAt,
      }))
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    setRecentInterviews(items.slice(0, 4));
  }, []);

  useEffect(() => {
    let cancelled = false;
    const init = async () => {
      setLoading(true);
      const [skills, resumes, loadedJobs, loadedReports, sessions] = await Promise.all([
        config.loadSkills(),
        config.loadResumes(),
        jobApi.list().catch(() => [] as JobItem[]),
        jobMatchApi.list().catch(() => [] as JobMatchReport[]),
        interviewApi.listSessions().catch(() => [] as TextSessionMeta[]),
      ]);
      if (cancelled) return;
      const usableJobs = loadedJobs.filter(job => job.status !== 'ARCHIVED');
      setJobs(loadedJobs);
      setReports(loadedReports);
      setSelectedJobId(usableJobs[0]?.id);
      if (resumes[0]) config.setResumeId(resumes[0].id);
      if (usableJobs.length === 0) setSource('PRACTICE');
      loadRecentInterviews(sessions, skills, loadedJobs);
      setLoading(false);
    };
    void init();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadRecentInterviews]);

  const handleStart = () => {
    if (source === 'JOB' && !selectedJob) return;
    const selectedSkill = config.selectedSkill;
    navigate('/interview', {
      state: {
        resumeId: config.resumeId,
        interviewConfig: {
          skillId: source === 'JOB' ? CUSTOM_SKILL_ID : config.skillId,
          skillName: source === 'JOB' ? selectedJob?.title : selectedSkill?.name,
          difficulty: config.difficulty,
          questionCount: config.questionCount,
          llmProvider: config.llmProvider,
          jdText: source === 'JOB' ? selectedJob?.jdText : undefined,
          customCategories: source === 'JOB' ? selectedJob?.parsedCategories : undefined,
          jobId: source === 'JOB' ? selectedJob?.id : undefined,
          matchReportId: source === 'JOB' ? selectedReport?.id : undefined,
          trainingMode: source === 'JOB' ? 'JOB_TARGETED' : config.trainingMode,
          userFocus: config.userFocus.trim() || undefined,
        },
      },
    });
  };

  const canStart = source === 'JOB' ? Boolean(selectedJob) : Boolean(config.skillId);

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <header className="relative overflow-hidden rounded-[28px] bg-slate-950 px-6 py-7 text-white shadow-xl shadow-slate-900/10 sm:px-8">
        <div className="absolute inset-0 opacity-30 [background-image:radial-gradient(circle_at_1px_1px,rgba(255,255,255,.16)_1px,transparent_0)] [background-size:24px_24px]" />
        <div className="absolute -right-16 -top-28 h-64 w-64 rounded-full bg-lime-300/15 blur-3xl" />
        <div className="relative flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="mb-4 inline-flex items-center gap-2 font-mono text-xs font-bold uppercase tracking-[0.18em] text-lime-300">
              <Zap className="h-4 w-4" /> Adaptive interview
            </div>
            <h1 className="max-w-2xl text-3xl font-black tracking-tight sm:text-4xl">
              开始一场有目标的面试
            </h1>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300">
              选择已保存岗位，Agent 会结合 JD、简历、历史画像和本次要求规划题目；也可以脱离岗位做专项训练。
            </p>
          </div>
          <div className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/[0.06] px-4 py-3 backdrop-blur">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-lime-300 text-slate-950">
              <Target className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs text-slate-400">当前训练上下文</p>
              <p className="mt-0.5 max-w-52 truncate text-sm font-semibold">
                {source === 'JOB' ? selectedJob?.title ?? '等待选择岗位' : config.selectedSkill?.name ?? '自由专项练习'}
              </p>
            </div>
          </div>
        </div>
      </header>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(300px,0.65fr)]">
        <section className="overflow-hidden rounded-[24px] border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <div className="border-b border-slate-100 p-5 dark:border-slate-700 sm:p-6">
            <StepTitle number="01" title="选择训练来源" description="岗位数据只维护一次，面试直接复用" />
            <div className="mt-5 grid grid-cols-2 gap-2 rounded-2xl bg-slate-100 p-1.5 dark:bg-slate-900/60">
              <SourceButton
                active={source === 'JOB'}
                icon={BriefcaseBusiness}
                title="目标岗位"
                description="使用已保存 JD"
                onClick={() => setSource('JOB')}
              />
              <SourceButton
                active={source === 'PRACTICE'}
                icon={Crosshair}
                title="专项练习"
                description="不绑定具体岗位"
                onClick={() => setSource('PRACTICE')}
              />
            </div>
          </div>

          <div className="p-5 sm:p-6">
            {loading ? (
              <div className="flex min-h-56 items-center justify-center text-sm text-slate-400">
                <Loader2 className="mr-2 h-5 w-5 animate-spin" /> 正在读取训练上下文
              </div>
            ) : source === 'JOB' ? (
              <JobSelector
                jobs={activeJobs}
                selectedJobId={selectedJobId}
                onSelect={setSelectedJobId}
              />
            ) : (
              <PracticeSelector config={config} />
            )}

            <div className="my-6 h-px bg-slate-100 dark:bg-slate-700" />
            <StepTitle number="02" title="设置本轮强度" description="只保留真正影响 Agent 蓝图的参数" />

            <div className="mt-5 grid gap-5 md:grid-cols-2">
              <label className="block">
                <span className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
                  <FileStack className="h-4 w-4 text-slate-400" /> 使用简历
                </span>
                <select
                  value={config.resumeId ?? ''}
                  onChange={event => config.setResumeId(event.target.value ? Number(event.target.value) : undefined)}
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-3 text-sm text-slate-800 outline-none transition focus:border-slate-400 dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                >
                  <option value="">不绑定简历</option>
                  {config.resumes.map(resume => (
                    <option key={resume.id} value={resume.id}>{resume.filename}</option>
                  ))}
                </select>
              </label>

              <div>
                <p className="mb-2 text-sm font-semibold text-slate-700 dark:text-slate-200">难度</p>
                <div className="grid grid-cols-3 gap-2">
                  {DIFFICULTY_OPTIONS.map(option => (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => config.setDifficulty(option.value)}
                      className={`rounded-xl border px-2 py-2.5 text-center transition ${config.difficulty === option.value
                        ? 'border-slate-950 bg-slate-950 text-white dark:border-lime-300 dark:bg-lime-300 dark:text-slate-950'
                        : 'border-slate-200 bg-white text-slate-500 hover:border-slate-400 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300'
                      }`}
                    >
                      <span className="block text-xs font-bold">{option.label}</span>
                      <span className="mt-0.5 block text-[10px] opacity-70">{option.desc}</span>
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div className="mt-5 grid gap-5 md:grid-cols-[0.65fr_1.35fr]">
              <div>
                <p className="mb-2 text-sm font-semibold text-slate-700 dark:text-slate-200">题目数量</p>
                <div className="flex rounded-xl border border-slate-200 p-1 dark:border-slate-700">
                  {[6, 8, 10].map(count => (
                    <button
                      key={count}
                      type="button"
                      onClick={() => config.setQuestionCount(count)}
                      className={`flex-1 rounded-lg py-2 text-xs font-bold transition ${config.questionCount === count
                        ? 'bg-slate-100 text-slate-950 dark:bg-slate-700 dark:text-white'
                        : 'text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'
                      }`}
                    >
                      {count} 题
                    </button>
                  ))}
                </div>
              </div>
              <label className="block">
                <span className="mb-2 block text-sm font-semibold text-slate-700 dark:text-slate-200">本轮特别要求（可选）</span>
                <input
                  value={config.userFocus}
                  onChange={event => config.setUserFocus(event.target.value)}
                  placeholder="例如：重点追问 Redis 一致性和线上故障定位"
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-slate-400 dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                />
              </label>
            </div>

            <div className="mt-6 flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/40 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Agent 将综合</p>
                <p className="mt-1 truncate text-sm font-semibold text-slate-800 dark:text-white">
                  {source === 'JOB' ? '岗位 JD + ' : ''}{config.resumeId ? '简历证据 + ' : ''}历史能力画像 + 本轮回答
                </p>
              </div>
              <button
                type="button"
                onClick={handleStart}
                disabled={!canStart}
                className="group inline-flex shrink-0 items-center justify-center gap-2 rounded-xl bg-lime-300 px-5 py-3 text-sm font-black text-slate-950 shadow-lg shadow-lime-300/15 transition hover:-translate-y-0.5 hover:bg-lime-200 disabled:cursor-not-allowed disabled:opacity-40"
              >
                开始面试
                <ArrowRight className="h-4 w-4 transition group-hover:translate-x-0.5" />
              </button>
            </div>
          </div>
        </section>

        <RecentInterviews items={recentInterviews} loading={loading} onOpen={id => navigate(`/interviews/${id}`)} />
      </div>
    </div>
  );
}

function StepTitle({ number, title, description }: { number: string; title: string; description: string }) {
  return (
    <div className="flex items-start gap-3">
      <span className="font-mono text-xs font-black text-slate-300 dark:text-slate-600">{number}</span>
      <div>
        <h2 className="font-bold text-slate-900 dark:text-white">{title}</h2>
        <p className="mt-0.5 text-xs text-slate-400">{description}</p>
      </div>
    </div>
  );
}

function SourceButton({
  active,
  icon: Icon,
  title,
  description,
  onClick,
}: {
  active: boolean;
  icon: typeof BriefcaseBusiness;
  title: string;
  description: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-3 rounded-xl px-3 py-3 text-left transition ${active
        ? 'bg-white text-slate-950 shadow-sm dark:bg-slate-700 dark:text-white'
        : 'text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-white'
      }`}
    >
      <Icon className={`h-4 w-4 ${active ? 'text-emerald-600 dark:text-lime-300' : ''}`} />
      <span>
        <span className="block text-sm font-bold">{title}</span>
        <span className="mt-0.5 block text-[10px] opacity-65">{description}</span>
      </span>
    </button>
  );
}

function JobSelector({
  jobs,
  selectedJobId,
  onSelect,
}: {
  jobs: JobItem[];
  selectedJobId?: number;
  onSelect: (id: number) => void;
}) {
  if (jobs.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 px-5 py-10 text-center dark:border-slate-700">
        <BriefcaseBusiness className="mx-auto h-7 w-7 text-slate-300" />
        <p className="mt-3 text-sm font-semibold text-slate-700 dark:text-slate-200">还没有可用岗位</p>
        <p className="mt-1 text-xs text-slate-400">先保存一份 JD，再回来进行岗位定向面试。</p>
        <Link to="/jobs" className="mt-4 inline-flex items-center gap-2 text-sm font-bold text-emerald-600 dark:text-lime-300">
          去岗位中心 <ArrowRight className="h-4 w-4" />
        </Link>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <p className="text-sm font-semibold text-slate-700 dark:text-slate-200">选择已保存岗位</p>
        <Link to="/jobs" className="inline-flex items-center gap-1 text-xs font-semibold text-slate-400 transition hover:text-slate-700 dark:hover:text-white">
          <Plus className="h-3.5 w-3.5" /> 管理岗位
        </Link>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        {jobs.map(job => {
          const active = job.id === selectedJobId;
          return (
            <button
              key={job.id}
              type="button"
              onClick={() => onSelect(job.id)}
              className={`relative overflow-hidden rounded-2xl border p-4 text-left transition ${active
                ? 'border-emerald-500 bg-emerald-50/60 shadow-sm dark:border-lime-300/60 dark:bg-lime-300/[0.06]'
                : 'border-slate-200 hover:border-slate-400 dark:border-slate-700 dark:hover:border-slate-500'
              }`}
            >
              {active && (
                <span className="absolute right-3 top-3 flex h-5 w-5 items-center justify-center rounded-full bg-emerald-600 text-white dark:bg-lime-300 dark:text-slate-950">
                  <Check className="h-3 w-3" />
                </span>
              )}
              <p className="pr-7 text-sm font-bold text-slate-900 dark:text-white">{job.title}</p>
              <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-slate-400">
                {job.company && <span className="inline-flex items-center gap-1"><Building2 className="h-3 w-3" />{job.company}</span>}
                {job.location && <span className="inline-flex items-center gap-1"><MapPin className="h-3 w-3" />{job.location}</span>}
              </div>
              <div className="mt-3 flex flex-wrap gap-1.5">
                {job.parsedCategories.slice(0, 3).map(category => (
                  <span key={category.key} className="rounded-md bg-white px-2 py-1 text-[10px] font-semibold text-slate-500 dark:bg-slate-800 dark:text-slate-300">
                    {category.label}
                  </span>
                ))}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function PracticeSelector({ config }: { config: ReturnType<typeof useInterviewConfig> }) {
  return (
    <div className="space-y-5">
      <div>
        <p className="mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">选择练习方向</p>
        {config.loadingSkills ? (
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        ) : (
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {config.skills.slice(0, 9).map(skill => {
              const active = config.skillId === skill.id;
              const Icon = getSkillIcon(skill.id);
              return (
                <button
                  key={skill.id}
                  type="button"
                  onClick={() => config.setSkillId(skill.id)}
                  className={`flex items-center gap-2.5 rounded-xl border px-3 py-3 text-left text-xs font-semibold transition ${active
                    ? 'border-slate-950 bg-slate-950 text-white dark:border-lime-300 dark:bg-lime-300 dark:text-slate-950'
                    : 'border-slate-200 text-slate-600 hover:border-slate-400 dark:border-slate-700 dark:text-slate-300'
                  }`}
                >
                  {Icon ? <Icon className="h-4 w-4 shrink-0" /> : <FileText className="h-4 w-4 shrink-0" />}
                  <span className="truncate">{skill.name}</span>
                </button>
              );
            })}
          </div>
        )}
      </div>
      <div>
        <p className="mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">训练策略</p>
        <div className="grid gap-2 sm:grid-cols-3">
          {STRATEGIES.map(([value, label, description]) => (
            <button
              key={value}
              type="button"
              onClick={() => config.setTrainingMode(value)}
              className={`rounded-xl border px-3 py-3 text-left transition ${config.trainingMode === value
                ? 'border-emerald-500 bg-emerald-50/70 dark:border-lime-300/50 dark:bg-lime-300/[0.06]'
                : 'border-slate-200 dark:border-slate-700'
              }`}
            >
              <span className="block text-xs font-bold text-slate-800 dark:text-white">{label}</span>
              <span className="mt-1 block text-[10px] leading-4 text-slate-400">{description}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function RecentInterviews({
  items,
  loading,
  onOpen,
}: {
  items: RecentInterviewItem[];
  loading: boolean;
  onOpen: (id: string) => void;
}) {
  return (
    <aside className="h-fit rounded-[24px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm font-bold text-slate-900 dark:text-white">
            <History className="h-4 w-4 text-emerald-600 dark:text-lime-300" /> 最近训练
          </div>
          <p className="mt-1 text-xs text-slate-400">继续复盘，而不是重复开新会话</p>
        </div>
        <Link to="/interviews" className="text-xs font-semibold text-slate-400 hover:text-slate-700 dark:hover:text-white">全部</Link>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Loader2 className="h-5 w-5 animate-spin text-slate-400" /></div>
      ) : items.length === 0 ? (
        <div className="mt-5 rounded-2xl border border-dashed border-slate-200 px-4 py-10 text-center dark:border-slate-700">
          <Clock3 className="mx-auto h-6 w-6 text-slate-300" />
          <p className="mt-3 text-xs text-slate-400">完成首场面试后，记录会出现在这里。</p>
        </div>
      ) : (
        <div className="mt-5 space-y-2">
          {items.map((item, index) => {
            const evaluating = item.evaluateStatus === 'PENDING' || item.evaluateStatus === 'PROCESSING';
            return (
              <motion.button
                key={item.id}
                type="button"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.04 }}
                onClick={() => onOpen(item.id)}
                className="group flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-3 text-left transition hover:border-slate-200 hover:bg-slate-50 dark:hover:border-slate-700 dark:hover:bg-slate-900/50"
              >
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-500 dark:bg-slate-700 dark:text-slate-300">
                  <FileText className="h-4 w-4" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-xs font-bold text-slate-800 dark:text-white">{item.title}</p>
                  <p className="mt-1 text-[10px] text-slate-400">{formatDateTime(item.createdAt)}</p>
                </div>
                {evaluating ? (
                  <RefreshCw className="h-4 w-4 animate-spin text-sky-500" />
                ) : item.overallScore != null ? (
                  <span className={`font-mono text-sm font-black ${getScoreTextColor(item.overallScore)}`}>{item.overallScore}</span>
                ) : (
                  <ChevronRight className="h-4 w-4 text-slate-300 transition group-hover:translate-x-0.5" />
                )}
              </motion.button>
            );
          })}
        </div>
      )}
    </aside>
  );
}
