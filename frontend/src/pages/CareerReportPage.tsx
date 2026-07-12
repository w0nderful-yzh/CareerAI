import { useEffect, useMemo, useState } from 'react';
import type { ComponentType, ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft,
  BriefcaseBusiness,
  CheckCircle2,
  Download,
  FileText,
  Loader2,
  Sparkles,
  Target,
  TrendingUp,
} from 'lucide-react';
import { careerReportApi, type CareerReport } from '../api/careerReports';
import { getErrorMessage } from '../api/request';
import { formatDate } from '../utils/date';

export default function CareerReportPage() {
  const { matchReportId } = useParams<{ matchReportId: string }>();
  const navigate = useNavigate();
  const [report, setReport] = useState<CareerReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');

  const numericMatchReportId = useMemo(() => {
    const value = Number(matchReportId);
    return Number.isFinite(value) ? value : 0;
  }, [matchReportId]);

  useEffect(() => {
    if (!numericMatchReportId) {
      setError('报告不存在');
      setLoading(false);
      return;
    }
    careerReportApi.get(numericMatchReportId)
      .then(setReport)
      .catch(err => setError(getErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [numericMatchReportId]);

  const exportPdf = async () => {
    if (!report) return;
    setExporting(true);
    try {
      const blob = await careerReportApi.exportPdf(report.matchReportId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `求职综合报告_${report.job.title}_${report.resume.filename}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setExporting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary-500" />
      </div>
    );
  }

  if (error || !report) {
    return (
      <div className="mx-auto max-w-3xl rounded-3xl border border-red-100 bg-red-50 p-8 text-center text-red-700 dark:border-red-900/40 dark:bg-red-900/20 dark:text-red-200">
        <p>{error || '综合报告不存在'}</p>
        <button
          type="button"
          onClick={() => navigate('/jobs')}
          className="mt-4 rounded-xl bg-red-600 px-4 py-2 text-sm font-semibold text-white"
        >
          返回岗位中心
        </button>
      </div>
    );
  }

  const interviewScore = report.latestInterview?.overallScore;
  const readinessScore = report.improvementPlan?.readinessScore;

  return (
    <div className="mx-auto max-w-6xl">
      <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <button
          type="button"
          onClick={() => navigate('/jobs')}
          className="inline-flex w-fit items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
        >
          <ArrowLeft className="h-4 w-4" />
          返回岗位中心
        </button>
        <button
          type="button"
          onClick={exportPdf}
          disabled={exporting}
          className="inline-flex w-fit items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:-translate-y-0.5 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-white dark:text-slate-900"
        >
          {exporting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
          导出 PDF
        </button>
      </div>

      <motion.header
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        className="overflow-hidden rounded-[2rem] border border-slate-200 bg-slate-950 text-white shadow-xl dark:border-slate-700"
      >
        <div className="relative p-7 md:p-9">
          <div className="absolute right-8 top-8 h-32 w-32 rounded-full bg-primary-500/20 blur-3xl" />
          <div className="relative">
            <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/10 px-3 py-1 text-xs font-semibold text-primary-100">
              <Sparkles className="h-3.5 w-3.5" />
              CareerAI 综合求职报告
            </div>
            <h1 className="text-3xl font-black tracking-tight md:text-4xl">{report.job.title}</h1>
            <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-300">
              基于简历、JD、岗位匹配、模拟面试复盘和改进计划生成。它不是单点分析，而是一条完整求职闭环的当前状态快照。
            </p>
            <div className="mt-5 flex flex-wrap gap-3 text-xs text-slate-300">
              <span className="rounded-full bg-white/10 px-3 py-1">简历：{report.resume.filename}</span>
              {report.job.company && <span className="rounded-full bg-white/10 px-3 py-1">公司：{report.job.company}</span>}
              {report.job.location && <span className="rounded-full bg-white/10 px-3 py-1">地点：{report.job.location}</span>}
              <span className="rounded-full bg-white/10 px-3 py-1">生成：{formatDate(report.generatedAt)}</span>
            </div>
          </div>
        </div>
      </motion.header>

      <div className="mt-6 grid gap-4 md:grid-cols-3">
        <ScoreCard icon={Target} label="岗位匹配度" value={report.match.overallScore} tone="primary" />
        <ScoreCard icon={TrendingUp} label="面试得分" value={interviewScore ?? null} tone="emerald" />
        <ScoreCard icon={FileText} label="简历准备度" value={readinessScore ?? null} tone="amber" />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
        <ReportSection title="简历-岗位匹配" icon={BriefcaseBusiness}>
          <p className="text-sm leading-6 text-slate-600 dark:text-slate-300">{report.match.summary}</p>
          <div className="mt-4 grid grid-cols-3 gap-3">
            <MiniMetric label="技能" value={report.match.skillScore} />
            <MiniMetric label="项目" value={report.match.projectScore} />
            <MiniMetric label="关键词" value={report.match.keywordScore} />
          </div>
          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <BulletList title="匹配亮点" items={report.match.matchedHighlights} />
            <BulletList title="主要差距" items={report.match.gaps} />
          </div>
        </ReportSection>

        <ReportSection title="简历改进计划" icon={FileText}>
          {report.improvementPlan ? (
            <>
              <p className="text-sm leading-6 text-slate-600 dark:text-slate-300">{report.improvementPlan.summary}</p>
              <div className="mt-5 grid gap-4 sm:grid-cols-2">
                <BulletList title="优先修改" items={report.improvementPlan.priorityFixes} />
                <BulletList title="简历文案" items={report.improvementPlan.resumeRewriteBullets} />
                <BulletList title="项目补强" items={report.improvementPlan.projectUpgradeTasks} />
                <BulletList title="面试练习" items={report.improvementPlan.interviewPracticeTasks} />
              </div>
            </>
          ) : (
            <EmptyHint text="还没有简历改进计划。回到岗位中心生成计划后，这里会自动汇总。" />
          )}
        </ReportSection>
      </div>

      <div className="mt-6">
        <ReportSection title="岗位模拟面试复盘" icon={CheckCircle2}>
          {report.latestInterview ? (
            <>
              <p className="text-sm leading-6 text-slate-600 dark:text-slate-300">
                {report.latestInterview.overallFeedback || '暂无总体反馈'}
              </p>
              {report.latestInterview.jobEvaluation && (
                <div className="mt-5 grid gap-4 lg:grid-cols-4">
                  <BulletList title="已覆盖能力" items={report.latestInterview.jobEvaluation.jdCoverage} />
                  <BulletList title="暴露短板" items={report.latestInterview.jobEvaluation.exposedGaps} />
                  <BulletList title="简历表达建议" items={report.latestInterview.jobEvaluation.resumeRewriteSuggestions} />
                  <BulletList title="下一步行动" items={report.latestInterview.jobEvaluation.nextActions} />
                </div>
              )}
            </>
          ) : (
            <EmptyHint text="还没有岗位面试复盘。完成一次岗位模拟面试后，报告会自动补齐这一段。" />
          )}
        </ReportSection>
      </div>
    </div>
  );
}

function ScoreCard({
  icon: Icon,
  label,
  value,
  tone,
}: {
  icon: ComponentType<{ className?: string }>;
  label: string;
  value: number | null;
  tone: 'primary' | 'emerald' | 'amber';
}) {
  const toneClassName = {
    primary: 'from-primary-500 to-indigo-500',
    emerald: 'from-emerald-500 to-teal-500',
    amber: 'from-amber-400 to-orange-500',
  }[tone];

  return (
    <div className="rounded-3xl border border-slate-100 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
      <div className={`mb-4 flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br ${toneClassName} text-white shadow-sm`}>
        <Icon className="h-5 w-5" />
      </div>
      <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">{label}</p>
      <p className="mt-1 text-3xl font-black text-slate-900 dark:text-white">
        {value == null ? '--' : value}
        {value != null && <span className="ml-1 text-sm font-semibold text-slate-400">分</span>}
      </p>
    </div>
  );
}

function ReportSection({
  title,
  icon: Icon,
  children,
}: {
  title: string;
  icon: ComponentType<{ className?: string }>;
  children: ReactNode;
}) {
  return (
    <section className="rounded-[1.7rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
      <div className="mb-4 flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200">
          <Icon className="h-5 w-5" />
        </div>
        <h2 className="text-lg font-black text-slate-900 dark:text-white">{title}</h2>
      </div>
      {children}
    </section>
  );
}

function MiniMetric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl bg-slate-50 px-3 py-3 text-center dark:bg-slate-900/60">
      <p className="text-lg font-black text-slate-900 dark:text-white">{value}</p>
      <p className="text-[11px] font-semibold text-slate-400">{label}</p>
    </div>
  );
}

function BulletList({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <p className="mb-2 text-xs font-black text-slate-500 dark:text-slate-400">{title}</p>
      <div className="space-y-2">
        {(items.length > 0 ? items : ['暂无条目']).map(item => (
          <div key={item} className="flex items-start gap-2 text-sm leading-5 text-slate-600 dark:text-slate-300">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-primary-500" />
            <span>{item}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function EmptyHint({ text }: { text: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-400">
      {text}
    </div>
  );
}
