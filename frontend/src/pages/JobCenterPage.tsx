import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  BriefcaseBusiness,
  Building2,
  CheckCircle2,
  ChevronDown,
  ClipboardCheck,
  Download,
  ExternalLink,
  FileText,
  Loader2,
  MapPin,
  Play,
  Plus,
  Sparkles,
  Target,
  Trash2,
} from 'lucide-react';
import { interviewApi, type TextSessionMeta } from '../api/interview';
import { getErrorMessage } from '../api/request';
import { historyApi, type ResumeListItem } from '../api/history';
import { jobApi, type JobItem, type JobStatus } from '../api/jobs';
import { jobMatchApi, type JobMatchReport } from '../api/jobMatches';
import { careerReportApi } from '../api/careerReports';
import {
  resumeImprovementPlanApi,
  type ResumeImprovementPlan,
} from '../api/resumeImprovementPlans';
import type { CategoryDTO } from '../api/skill';
import { CUSTOM_SKILL_ID } from '../hooks/useInterviewConfig';

const STATUS_LABELS: Record<JobStatus, string> = {
  TRACKING: '关注中',
  APPLIED: '已投递',
  INTERVIEW: '面试中',
  OFFER: 'Offer',
  REJECTED: '未通过',
  ARCHIVED: '已归档',
};

const STATUS_STYLES: Record<JobStatus, string> = {
  TRACKING: 'bg-sky-50 text-sky-700 border-sky-100 dark:bg-sky-900/20 dark:text-sky-300 dark:border-sky-800/40',
  APPLIED: 'bg-indigo-50 text-indigo-700 border-indigo-100 dark:bg-indigo-900/20 dark:text-indigo-300 dark:border-indigo-800/40',
  INTERVIEW: 'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-900/20 dark:text-amber-300 dark:border-amber-800/40',
  OFFER: 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-900/20 dark:text-emerald-300 dark:border-emerald-800/40',
  REJECTED: 'bg-rose-50 text-rose-700 border-rose-100 dark:bg-rose-900/20 dark:text-rose-300 dark:border-rose-800/40',
  ARCHIVED: 'bg-slate-50 text-slate-600 border-slate-100 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700',
};

const INTERVIEW_STATUS_LABELS: Record<string, string> = {
  CREATED: '待开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '待评估',
  EVALUATED: '已评估',
};

export default function JobCenterPage() {
  const navigate = useNavigate();
  const [jobs, setJobs] = useState<JobItem[]>([]);
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [reportsByJob, setReportsByJob] = useState<Record<number, JobMatchReport[]>>({});
  const [interviewsByJob, setInterviewsByJob] = useState<Record<number, TextSessionMeta[]>>({});
  const [plansByReport, setPlansByReport] = useState<Record<number, ResumeImprovementPlan[]>>({});
  const [selectedResumeIds, setSelectedResumeIds] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [exportingReportId, setExportingReportId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [expandedReportIds, setExpandedReportIds] = useState<Set<number>>(new Set());

  const [title, setTitle] = useState('');
  const [company, setCompany] = useState('');
  const [location, setLocation] = useState('');
  const [sourceUrl, setSourceUrl] = useState('');
  const [jdText, setJdText] = useState('');
  const [parsedCategories, setParsedCategories] = useState<CategoryDTO[]>([]);

  const activeJobs = useMemo(
    () => jobs.filter(job => job.status !== 'ARCHIVED'),
    [jobs]
  );

  const loadJobs = async () => {
    setLoading(true);
    try {
      const [jobList, resumeList, reportList, interviewList, planList] = await Promise.all([
        jobApi.list(),
        historyApi.getResumes(),
        jobMatchApi.list(),
        interviewApi.listSessions(),
        resumeImprovementPlanApi.list(),
      ]);
      setJobs(jobList);
      setResumes(resumeList);
      setReportsByJob(groupReportsByJob(reportList));
      setInterviewsByJob(groupInterviewsByJob(interviewList));
      setPlansByReport(groupPlansByReport(planList));
      if (jobList.length === 0) setShowCreateForm(true);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadJobs();
  }, []);

  const handleParse = async () => {
    if (jdText.trim().length < 50) {
      setError('JD 内容至少 50 字，先多粘一点岗位描述。');
      return;
    }
    setParsing(true);
    setError('');
    try {
      const result = await jobApi.parseJd(jdText);
      setParsedCategories(result.categories);
      if (!title.trim() && result.suggestedTitle) {
        setTitle(result.suggestedTitle);
      }
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setParsing(false);
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      await jobApi.create({
        title,
        company,
        location,
        sourceUrl,
        jdText,
        parsedCategories,
      });
      resetForm();
      setShowCreateForm(false);
      await loadJobs();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const resetForm = () => {
    setTitle('');
    setCompany('');
    setLocation('');
    setSourceUrl('');
    setJdText('');
    setParsedCategories([]);
  };

  const updateStatus = async (job: JobItem, status: JobStatus) => {
    try {
      const updated = await jobApi.updateStatus(job.id, status);
      setJobs(prev => prev.map(item => item.id === updated.id ? updated : item));
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const deleteJob = async (job: JobItem) => {
    if (!confirm(`删除岗位「${job.title}」？`)) {
      return;
    }
    try {
      await jobApi.delete(job.id);
      setJobs(prev => prev.filter(item => item.id !== job.id));
      setReportsByJob(prev => {
        const next = { ...prev };
        delete next[job.id];
        return next;
      });
      setInterviewsByJob(prev => {
        const next = { ...prev };
        delete next[job.id];
        return next;
      });
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const openAgentPlan = (job: JobItem) => {
    const selectedResumeId = getSelectedResumeId(job);
    if (!selectedResumeId) {
      setError('请先上传并分析一份简历，再交给 Agent 规划。');
      return;
    }
    navigate('/agent', {
      state: {
        jobId: job.id,
        resumeId: Number(selectedResumeId),
        goal: '分析所选简历与目标岗位的匹配证据，并生成可执行的准备计划',
      },
    });
  };

  const exportCareerReport = async (report: JobMatchReport) => {
    setExportingReportId(report.id);
    setError('');
    try {
      const blob = await careerReportApi.exportPdf(report.id);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `求职综合报告_${report.jobTitle}_${report.resumeFilename}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setExportingReportId(null);
    }
  };

  const getSelectedResumeId = (job: JobItem) => (
    selectedResumeIds[job.id] ?? resumes[0]?.id?.toString() ?? ''
  );

  const getSelectedResumeReport = (job: JobItem) => {
    const selectedResumeId = Number(getSelectedResumeId(job));
    if (!selectedResumeId) {
      return undefined;
    }
    return reportsByJob[job.id]?.find(report => report.resumeId === selectedResumeId);
  };

  const getLatestInterview = (job: JobItem) => {
    const selectedResumeId = Number(getSelectedResumeId(job));
    return interviewsByJob[job.id]?.find(interview => (
      !selectedResumeId || interview.resumeId === selectedResumeId
    ));
  };

  const startInterview = (job: JobItem) => {
    const selectedResumeId = getSelectedResumeId(job);
    const selectedReport = getSelectedResumeReport(job);

    navigate('/interview', {
      state: {
        resumeId: selectedResumeId ? Number(selectedResumeId) : undefined,
        interviewConfig: {
          skillId: CUSTOM_SKILL_ID,
          difficulty: 'mid',
          questionCount: 6,
          jdText: job.jdText,
          customCategories: job.parsedCategories,
          jobId: job.id,
          matchReportId: selectedReport?.id,
          trainingMode: 'JOB_TARGETED',
        },
      },
    });
  };

  return (
    <div className="mx-auto max-w-6xl">
      <div className="mb-8 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="mb-3 inline-flex items-center gap-2 font-mono text-xs font-bold uppercase tracking-[0.16em] text-emerald-600 dark:text-lime-300">
            <Sparkles className="h-3.5 w-3.5" />
            Job workspace
          </div>
          <h1 className="flex items-center gap-3 text-2xl font-bold text-slate-800 dark:text-white">
            <BriefcaseBusiness className="h-7 w-7 text-primary-500" />
            目标岗位
          </h1>
          <p className="mt-1 text-slate-500 dark:text-slate-400">
            JD 在这里维护一次，匹配规划和模拟面试直接复用。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="grid grid-cols-3 gap-2 text-center">
            <Metric label="目标岗位" value={activeJobs.length} />
            <Metric label="已投递" value={jobs.filter(job => job.status === 'APPLIED').length} />
            <Metric label="匹配报告" value={Object.values(reportsByJob).reduce((sum, reports) => sum + reports.length, 0)} />
          </div>
          <button
            type="button"
            onClick={() => setShowCreateForm(value => !value)}
            className="inline-flex items-center gap-2 rounded-xl bg-slate-950 px-4 py-3 text-sm font-bold text-white transition hover:-translate-y-0.5 dark:bg-lime-300 dark:text-slate-950"
          >
            <Plus className="h-4 w-4" />
            {showCreateForm ? '收起' : '新增岗位'}
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900/40 dark:bg-red-900/20 dark:text-red-200">
          {error}
        </div>
      )}

      <div className={`grid gap-6 ${showCreateForm ? 'lg:grid-cols-[0.82fr_1.18fr]' : ''}`}>
        {showCreateForm && <motion.form
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          onSubmit={handleSubmit}
          className="rounded-[1.6rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800"
        >
          <div className="mb-5 flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-900 text-white dark:bg-white dark:text-slate-900">
              <Plus className="h-5 w-5" />
            </div>
            <div>
              <h2 className="font-bold text-slate-900 dark:text-white">保存新岗位</h2>
              <p className="text-xs text-slate-400">粘贴 JD，解析考察方向后保存。</p>
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <Input label="岗位名称" value={title} onChange={setTitle} placeholder="Java 后端开发实习生" />
            <Input label="公司" value={company} onChange={setCompany} placeholder="阿里云 / 字节 / 腾讯" />
            <Input label="地点" value={location} onChange={setLocation} placeholder="杭州 / 上海 / 远程" />
            <Input label="来源链接" value={sourceUrl} onChange={setSourceUrl} placeholder="https://..." />
          </div>

          <label className="mt-4 block">
            <span className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
              <FileText className="h-4 w-4" />
              JD 原文
            </span>
            <textarea
              value={jdText}
              onChange={event => {
                setJdText(event.target.value);
                setParsedCategories([]);
              }}
              rows={10}
              required
              placeholder="粘贴岗位职责、任职要求、技术栈..."
              className="w-full resize-none rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-primary-300 focus:bg-white focus:ring-4 focus:ring-primary-100 dark:border-slate-700 dark:bg-slate-900/60 dark:text-white dark:focus:border-primary-500 dark:focus:ring-primary-900/30"
            />
          </label>

          <div className="mt-4 flex flex-wrap gap-2">
            {parsedCategories.map(category => (
              <span
                key={`${category.key}-${category.label}`}
                className="rounded-full bg-primary-50 px-3 py-1 text-xs font-semibold text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
              >
                {category.label}
                <span className="ml-1 text-[10px] opacity-70">{category.priority}</span>
              </span>
            ))}
          </div>

          <div className="mt-5 grid gap-3 sm:grid-cols-2">
            <button
              type="button"
              onClick={handleParse}
              disabled={parsing || !jdText.trim()}
              className="flex items-center justify-center gap-2 rounded-xl border border-primary-200 bg-primary-50 px-4 py-3 text-sm font-semibold text-primary-700 transition hover:bg-primary-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-primary-800/50 dark:bg-primary-900/20 dark:text-primary-300"
            >
              {parsing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
              解析 JD
            </button>
            <button
              type="submit"
              disabled={saving || !jdText.trim()}
              className="flex items-center justify-center gap-2 rounded-xl bg-slate-900 px-4 py-3 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-white dark:text-slate-900"
            >
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
              保存岗位
            </button>
          </div>
        </motion.form>}

        <section className="rounded-[1.6rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <h2 className="font-bold text-slate-900 dark:text-white">岗位工作区</h2>
              <p className="mt-1 text-xs text-slate-400">选简历后，直接让 Agent 规划或开始岗位面试。</p>
            </div>
            <button
              onClick={loadJobs}
              className="text-sm font-medium text-primary-500 hover:text-primary-600"
            >
              刷新
            </button>
          </div>

          {loading ? (
            <div className="flex justify-center py-16">
              <Loader2 className="h-7 w-7 animate-spin text-primary-500" />
            </div>
          ) : jobs.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-200 py-16 text-center dark:border-slate-700">
              <BriefcaseBusiness className="mx-auto mb-3 h-8 w-8 text-slate-300" />
              <p className="text-sm text-slate-400">还没有目标岗位，先添加一个 JD。</p>
            </div>
          ) : (
            <div className="space-y-3">
              {jobs.map((job, index) => (
                <motion.article
                  key={job.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.03 }}
                  className="rounded-2xl border border-slate-100 bg-gradient-to-br from-white to-slate-50 p-4 dark:border-slate-700 dark:from-slate-800 dark:to-slate-900/60"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h3 className="truncate font-bold text-slate-900 dark:text-white">{job.title}</h3>
                      <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-slate-500 dark:text-slate-400">
                        {job.company && <span className="inline-flex items-center gap-1"><Building2 className="h-3.5 w-3.5" />{job.company}</span>}
                        {job.location && <span className="inline-flex items-center gap-1"><MapPin className="h-3.5 w-3.5" />{job.location}</span>}
                        {job.sourceUrl && (
                          <a
                            href={job.sourceUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1 text-primary-500 hover:text-primary-600"
                          >
                            <ExternalLink className="h-3.5 w-3.5" />
                            来源
                          </a>
                        )}
                      </div>
                    </div>
                    <select
                      value={job.status}
                      onChange={event => updateStatus(job, event.target.value as JobStatus)}
                      className={`rounded-xl border px-2.5 py-1.5 text-xs font-semibold outline-none ${STATUS_STYLES[job.status]}`}
                    >
                      {Object.entries(STATUS_LABELS).map(([value, label]) => (
                        <option key={value} value={value}>{label}</option>
                      ))}
                    </select>
                  </div>

                  <div className="mt-3 flex flex-wrap gap-2">
                    {job.parsedCategories.slice(0, 6).map(category => (
                      <span
                        key={`${job.id}-${category.key}`}
                        className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-medium text-slate-600 dark:bg-slate-700 dark:text-slate-300"
                      >
                        {category.label}
                      </span>
                    ))}
                  </div>

                  <div className="mt-4 rounded-2xl border border-primary-100 bg-primary-50/60 p-3 dark:border-primary-900/40 dark:bg-primary-900/10">
                    <div className="mb-2 flex items-center gap-2 text-xs font-bold text-primary-700 dark:text-primary-300">
                      <Target className="h-3.5 w-3.5" />
                      简历-岗位匹配
                    </div>
                    <div className="grid gap-2 sm:grid-cols-[1fr_auto]">
                      <select
                        value={getSelectedResumeId(job)}
                        onChange={event => setSelectedResumeIds(prev => ({
                          ...prev,
                          [job.id]: event.target.value,
                        }))}
                        disabled={resumes.length === 0}
                        className="min-w-0 rounded-xl border border-primary-100 bg-white px-3 py-2 text-xs font-medium text-slate-700 outline-none transition focus:border-primary-300 focus:ring-4 focus:ring-primary-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-primary-900/50 dark:bg-slate-900 dark:text-slate-200 dark:focus:ring-primary-900/30"
                      >
                        {resumes.length === 0 ? (
                          <option value="">暂无简历</option>
                        ) : resumes.map(resume => (
                          <option key={resume.id} value={resume.id}>
                            {resume.filename}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => openAgentPlan(job)}
                        disabled={resumes.length === 0}
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-slate-950 px-3 py-2 text-xs font-semibold text-white transition hover:-translate-y-0.5 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-lime-300 dark:text-slate-950 dark:hover:bg-lime-200"
                      >
                        <Sparkles className="h-3.5 w-3.5" />
                        Agent 规划
                      </button>
                    </div>
                    {getSelectedResumeReport(job) ? (
                      <MatchReportCard
                        report={getSelectedResumeReport(job)!}
                        latestPlan={plansByReport[getSelectedResumeReport(job)!.id]?.[0]}
                        exporting={exportingReportId === getSelectedResumeReport(job)!.id}
                        expanded={expandedReportIds.has(getSelectedResumeReport(job)!.id)}
                        onToggle={() => setExpandedReportIds(current => {
                          const next = new Set(current);
                          const reportId = getSelectedResumeReport(job)!.id;
                          if (next.has(reportId)) next.delete(reportId);
                          else next.add(reportId);
                          return next;
                        })}
                        onOpenAgent={() => openAgentPlan(job)}
                        onViewReport={() => navigate(`/career-reports/${getSelectedResumeReport(job)!.id}`)}
                        onExportReport={() => exportCareerReport(getSelectedResumeReport(job)!)}
                      />
                    ) : (
                      <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
                        选择一份简历，Agent 会读取 JD 与简历证据，完成匹配和准备规划。
                      </p>
                    )}
                  </div>

                  {getLatestInterview(job) && (
                    <LatestInterviewCard
                      interview={getLatestInterview(job)!}
                      onView={() => navigate(`/interviews/${getLatestInterview(job)!.sessionId}`)}
                    />
                  )}

                  <div className="mt-4 flex gap-2">
                    <button
                      onClick={() => startInterview(job)}
                      className="flex flex-1 items-center justify-center gap-2 rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-xs font-bold text-slate-800 transition hover:-translate-y-0.5 hover:border-slate-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-800 dark:text-white"
                    >
                      <Play className="h-3.5 w-3.5" />
                      开始岗位面试
                    </button>
                    <button
                      onClick={() => deleteJob(job)}
                      className="rounded-xl border border-slate-200 px-3 py-2 text-slate-400 transition hover:border-red-200 hover:bg-red-50 hover:text-red-500 dark:border-slate-700 dark:hover:border-red-900/50 dark:hover:bg-red-900/20"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </motion.article>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

function groupReportsByJob(reports: JobMatchReport[]) {
  return reports.reduce<Record<number, JobMatchReport[]>>((acc, report) => {
    acc[report.jobId] = [...(acc[report.jobId] ?? []), report];
    return acc;
  }, {});
}

function groupInterviewsByJob(interviews: TextSessionMeta[]) {
  return interviews.reduce<Record<number, TextSessionMeta[]>>((acc, interview) => {
    if (!interview.jobId) {
      return acc;
    }
    acc[interview.jobId] = [...(acc[interview.jobId] ?? []), interview];
    return acc;
  }, {});
}

function groupPlansByReport(plans: ResumeImprovementPlan[]) {
  return plans.reduce<Record<number, ResumeImprovementPlan[]>>((acc, plan) => {
    acc[plan.matchReportId] = [...(acc[plan.matchReportId] ?? []), plan];
    return acc;
  }, {});
}

function LatestInterviewCard({
  interview,
  onView,
}: {
  interview: TextSessionMeta;
  onView: () => void;
}) {
  const statusLabel = INTERVIEW_STATUS_LABELS[interview.status] ?? interview.status;
  const scoreText = interview.overallScore == null ? '未评分' : `${interview.overallScore} 分`;

  return (
    <div className="mt-3 rounded-2xl border border-emerald-100 bg-emerald-50/70 p-3 dark:border-emerald-900/40 dark:bg-emerald-900/10">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="mb-1 flex items-center gap-2 text-xs font-bold text-emerald-700 dark:text-emerald-300">
            <ClipboardCheck className="h-3.5 w-3.5" />
            最近岗位面试
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {statusLabel} · {interview.totalQuestions} 题 · {scoreText}
          </p>
        </div>
        <button
          type="button"
          onClick={onView}
          className="rounded-xl bg-white px-3 py-2 text-xs font-semibold text-emerald-700 shadow-sm transition hover:-translate-y-0.5 hover:bg-emerald-50 dark:bg-slate-900 dark:text-emerald-300 dark:hover:bg-slate-800"
        >
          查看报告
        </button>
      </div>
    </div>
  );
}

function MatchReportCard({
  report,
  latestPlan,
  exporting,
  expanded,
  onToggle,
  onOpenAgent,
  onViewReport,
  onExportReport,
}: {
  report: JobMatchReport;
  latestPlan?: ResumeImprovementPlan;
  exporting: boolean;
  expanded: boolean;
  onToggle: () => void;
  onOpenAgent: () => void;
  onViewReport: () => void;
  onExportReport: () => void;
}) {
  return (
    <div className="mt-3 rounded-2xl border border-white/70 bg-white p-3 shadow-sm dark:border-slate-700 dark:bg-slate-900/70">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold text-slate-500 dark:text-slate-400">
            {report.resumeFilename}
          </p>
          <p className="mt-1 text-sm font-bold text-slate-900 dark:text-white">
            匹配度 {report.overallScore} 分
          </p>
        </div>
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-primary-500 to-indigo-500 text-sm font-black text-white shadow-sm">
          {report.overallScore}
        </div>
      </div>

      <p className="mt-3 line-clamp-2 text-xs leading-5 text-slate-600 dark:text-slate-300">
        {report.summary}
      </p>

      {expanded && (
        <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }}>
          <div className="mt-3 grid grid-cols-3 gap-2">
            <ScorePill label="技能" value={report.skillScore} />
            <ScorePill label="项目" value={report.projectScore} />
            <ScorePill label="关键词" value={report.keywordScore} />
          </div>

          {(report.evidenceMappings?.length ?? 0) > 0 && (
            <EvidenceCoverageSummary report={report} />
          )}

          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <ReportList title="匹配亮点" items={report.matchedHighlights} tone="good" />
            <ReportList title="优先补强" items={report.actionItems.length > 0 ? report.actionItems : report.gaps} tone="todo" />
          </div>
        </motion.div>
      )}

      <div className="mt-4 rounded-2xl border border-amber-100 bg-amber-50/70 p-3 dark:border-amber-900/40 dark:bg-amber-900/10">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs font-black text-amber-700 dark:text-amber-300">简历改进计划</p>
            <p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">
              {latestPlan ? `当前准备度 ${latestPlan.readinessScore} 分，可让 Agent 结合新证据更新。` : '让 Agent 把匹配差距转成可执行准备任务。'}
            </p>
          </div>
          <button
            type="button"
            onClick={onOpenAgent}
            className="inline-flex shrink-0 items-center justify-center gap-2 rounded-xl bg-slate-900 px-3 py-2 text-xs font-semibold text-white transition hover:-translate-y-0.5 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-white dark:text-slate-900"
          >
            <Sparkles className="h-3.5 w-3.5" />
            {latestPlan ? '让 Agent 更新' : '让 Agent 生成'}
          </button>
        </div>

        {expanded && latestPlan && <ImprovementPlanCard plan={latestPlan} />}

        <div className="mt-3 grid gap-2 sm:grid-cols-3">
          <button
            type="button"
            onClick={onToggle}
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          >
            <ChevronDown className={`h-3.5 w-3.5 transition ${expanded ? 'rotate-180' : ''}`} />
            {expanded ? '收起详情' : '证据详情'}
          </button>
          <button
            type="button"
            onClick={onViewReport}
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 transition hover:-translate-y-0.5 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
          >
            <FileText className="h-3.5 w-3.5" />
            查看综合报告
          </button>
          <button
            type="button"
            onClick={onExportReport}
            disabled={exporting}
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-900 bg-slate-900 px-3 py-2 text-xs font-semibold text-white transition hover:-translate-y-0.5 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50 dark:border-white dark:bg-white dark:text-slate-900"
          >
            {exporting ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Download className="h-3.5 w-3.5" />}
            导出综合 PDF
          </button>
        </div>
      </div>
    </div>
  );
}

function ImprovementPlanCard({ plan }: { plan: ResumeImprovementPlan }) {
  return (
    <div className="mt-3 rounded-2xl border border-white/80 bg-white p-3 shadow-sm dark:border-slate-700 dark:bg-slate-900/70">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-slate-900 dark:text-white">
            准备度 {plan.readinessScore} 分
          </p>
          <p className="mt-1 text-xs leading-5 text-slate-600 dark:text-slate-300">
            {plan.summary}
          </p>
        </div>
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-amber-400 to-orange-500 text-sm font-black text-white shadow-sm">
          {plan.readinessScore}
        </div>
      </div>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <PlanList title="优先修改" items={plan.priorityFixes} />
        <PlanList title="简历文案" items={plan.resumeRewriteBullets} />
        <PlanList title="项目补强" items={plan.projectUpgradeTasks} />
        <PlanList title="面试练习" items={plan.interviewPracticeTasks} />
      </div>

      {plan.learningTasks.length > 0 && (
        <div className="mt-3">
          <PlanList title="学习任务" items={plan.learningTasks} />
        </div>
      )}

      {(plan.preparationTasks?.length ?? 0) > 0 && (
        <div className="mt-3 rounded-xl border border-slate-100 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-800/60">
          <p className="mb-2 text-[11px] font-bold text-slate-500 dark:text-slate-400">结构化准备任务</p>
          <div className="space-y-2">
            {plan.preparationTasks.slice(0, 4).map(task => (
              <div key={task.id} className="flex items-start gap-2 text-[11px] leading-4 text-slate-600 dark:text-slate-300">
                <span className={`shrink-0 rounded px-1.5 py-0.5 font-mono text-[9px] font-black ${task.priority === 'P0' ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-700'}`}>{task.priority}</span>
                <span className="flex-1">{task.title}</span>
                <span className="shrink-0 text-slate-400">{task.suggestedDays} 天</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function EvidenceCoverageSummary({ report }: { report: JobMatchReport }) {
  const counts = report.evidenceMappings.reduce<Record<string, number>>((result, mapping) => {
    result[mapping.coverageType] = (result[mapping.coverageType] ?? 0) + 1;
    return result;
  }, {});
  const items = [
    ['充分支持', counts.SUPPORTED ?? 0, 'text-emerald-600'],
    ['表达缺失', counts.EXPRESSION_GAP ?? 0, 'text-sky-600'],
    ['证据不足', counts.EVIDENCE_GAP ?? 0, 'text-amber-600'],
    ['能力缺失', counts.CAPABILITY_GAP ?? 0, 'text-rose-600'],
  ] as const;
  return (
    <div className="mt-3 grid grid-cols-4 gap-1.5 rounded-xl border border-slate-100 bg-slate-50 p-2 dark:border-slate-700 dark:bg-slate-800/60">
      {items.map(([label, count, className]) => (
        <div key={label} className="text-center">
          <p className={`text-sm font-black ${className}`}>{count}</p>
          <p className="text-[9px] text-slate-400">{label}</p>
        </div>
      ))}
    </div>
  );
}

function PlanList({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <p className="mb-1.5 text-[11px] font-bold text-slate-500 dark:text-slate-400">{title}</p>
      <div className="space-y-1.5">
        {(items.length > 0 ? items.slice(0, 4) : ['暂无条目，请重新生成计划。']).map(item => (
          <div key={item} className="flex items-start gap-1.5 text-[11px] leading-4 text-slate-600 dark:text-slate-300">
            <CheckCircle2 className="mt-0.5 h-3 w-3 shrink-0 text-amber-500" />
            <span>{item}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function ScorePill({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl bg-slate-50 px-2.5 py-2 text-center dark:bg-slate-800">
      <p className="text-sm font-black text-slate-900 dark:text-white">{value}</p>
      <p className="text-[10px] font-semibold text-slate-400">{label}</p>
    </div>
  );
}

function ReportList({
  title,
  items,
  tone,
}: {
  title: string;
  items: string[];
  tone: 'good' | 'todo';
}) {
  const iconClassName = tone === 'good' ? 'text-emerald-500' : 'text-primary-500';

  return (
    <div>
      <p className="mb-1.5 text-[11px] font-bold text-slate-500 dark:text-slate-400">{title}</p>
      <div className="space-y-1.5">
        {(items.length > 0 ? items.slice(0, 3) : ['暂无详细条目，请重新生成报告。']).map(item => (
          <div key={item} className="flex items-start gap-1.5 text-[11px] leading-4 text-slate-600 dark:text-slate-300">
            <CheckCircle2 className={`mt-0.5 h-3 w-3 shrink-0 ${iconClassName}`} />
            <span>{item}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl border border-slate-100 bg-white px-5 py-3 shadow-sm dark:border-slate-700 dark:bg-slate-800">
      <p className="text-lg font-black text-slate-900 dark:text-white">{value}</p>
      <p className="text-xs text-slate-400">{label}</p>
    </div>
  );
}


function Input({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-semibold text-slate-500 dark:text-slate-400">{label}</span>
      <input
        value={value}
        onChange={event => onChange(event.target.value)}
        placeholder={placeholder}
        className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 outline-none transition focus:border-primary-300 focus:bg-white focus:ring-4 focus:ring-primary-100 dark:border-slate-700 dark:bg-slate-900/60 dark:text-white dark:focus:border-primary-500 dark:focus:ring-primary-900/30"
      />
    </label>
  );
}
