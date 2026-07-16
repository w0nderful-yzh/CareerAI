import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  ArrowUpRight,
  Bot,
  CheckCircle2,
  ClipboardCheck,
  Crosshair,
  Quote,
  ShieldCheck,
} from 'lucide-react';
import { historyApi, type InterviewClosure } from '../api/history';

interface InterviewClosureSectionProps {
  sessionId: string;
}

/** 展示 Agent 在面试结束后落库的业务产物，而不是再次生成一段对话总结。 */
export default function InterviewClosureSection({ sessionId }: InterviewClosureSectionProps) {
  const [closure, setClosure] = useState<InterviewClosure | null>(null);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setUnavailable(false);
    historyApi.getInterviewClosure(sessionId)
      .then(data => {
        if (active) setClosure(data);
      })
      .catch(() => {
        if (active) setUnavailable(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [sessionId]);

  if (loading) {
    return (
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800">
        <div className="h-2 animate-pulse bg-lime-300/80" />
        <div className="space-y-3 p-6">
          <div className="h-4 w-32 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
          <div className="h-8 w-2/3 animate-pulse rounded bg-slate-100 dark:bg-slate-700/60" />
          <div className="h-20 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-900/50" />
        </div>
      </div>
    );
  }

  if (unavailable || !closure) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-5 py-4 text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-800/40 dark:text-slate-400">
        这条历史记录还没有结束编排产物，原始评分和问答证据仍可正常查看。
      </div>
    );
  }

  const pendingTasks = closure.improvementTasks.filter(task => task.status !== 'DONE').length;

  return (
    <motion.section
      className="overflow-hidden rounded-2xl border border-slate-800 bg-[#111715] text-white shadow-xl shadow-slate-900/10"
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.08 }}
    >
      <div className="h-1.5 bg-lime-300" />
      <div className="relative overflow-hidden border-b border-white/10 px-6 py-6">
        <div className="absolute -right-16 -top-20 h-48 w-48 rounded-full border border-lime-200/10" />
        <div className="absolute -right-7 -top-10 h-28 w-28 rounded-full border border-lime-200/20" />
        <div className="relative flex flex-col gap-5 md:flex-row md:items-start md:justify-between">
          <div className="max-w-2xl">
            <div className="mb-3 flex items-center gap-2 text-lime-300">
              <Bot className="h-4 w-4" />
              <span className="font-mono text-xs font-bold uppercase tracking-[0.2em]">
                Agent closeout
              </span>
            </div>
            <h3 className="text-2xl font-black tracking-tight">结束编排与下一轮训练</h3>
            <p className="mt-3 text-sm leading-7 text-slate-300">{closure.summary}</p>
          </div>
          <span className={`w-fit rounded-full border px-3 py-1.5 text-xs font-bold ${closure.completionType === 'COMPLETE'
            ? 'border-lime-300/40 bg-lime-300/10 text-lime-200'
            : 'border-amber-300/40 bg-amber-300/10 text-amber-200'}`}>
            {closure.completionType === 'COMPLETE' ? '完整证据' : '部分评价'}
          </span>
        </div>

        <div className="relative mt-6 grid grid-cols-3 gap-px overflow-hidden rounded-xl bg-white/10">
          <Metric label="关键证据" value={closure.keyEvidence.length} />
          <Metric label="改进任务" value={closure.improvementTasks.length} />
          <Metric label="等待完成" value={pendingTasks} accent />
        </div>
      </div>

      <div className="grid gap-0 lg:grid-cols-[1.45fr_0.75fr]">
        <div className="border-b border-white/10 p-6 lg:border-b-0 lg:border-r">
          <SectionTitle icon={ClipboardCheck} label="可执行改进任务" />
          <div className="mt-4 space-y-3">
            {closure.improvementTasks.length ? closure.improvementTasks.map((task, index) => (
              <article
                key={task.idempotencyKey}
                className="group rounded-xl border border-white/10 bg-white/[0.035] p-4 transition hover:border-lime-200/30 hover:bg-white/[0.06]"
              >
                <div className="flex items-start gap-3">
                  <span className="mt-0.5 font-mono text-xs text-slate-500">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className={`rounded px-2 py-0.5 text-[10px] font-black tracking-wider ${priorityTone(task.priority)}`}>
                        {task.priority}
                      </span>
                      <span className="text-xs text-slate-400">
                        {task.category} · 来源第 {task.questionIndex + 1} 题
                      </span>
                    </div>
                    <h4 className="mt-2 font-bold leading-6 text-white">{task.title}</h4>
                    <p className="mt-1 text-sm leading-6 text-slate-400">{task.rationale}</p>
                    <div className="mt-3 flex items-start gap-2 rounded-lg border border-dashed border-white/10 bg-black/20 px-3 py-2.5 text-xs leading-5 text-slate-300">
                      <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-lime-300" />
                      <span><strong className="text-slate-100">验收：</strong>{task.verificationMethod}</span>
                    </div>
                  </div>
                </div>
              </article>
            )) : (
              <div className="rounded-xl border border-white/10 bg-white/[0.035] p-5 text-sm text-slate-400">
                本次没有从真实回答中识别出需要创建的改进任务。
              </div>
            )}
          </div>
        </div>

        <aside className="p-6">
          <SectionTitle icon={Crosshair} label="下一场建议" />
          <ol className="mt-4 space-y-3">
            {closure.nextInterviewSuggestions.map((suggestion, index) => (
              <li key={`${suggestion}-${index}`} className="flex gap-3 text-sm leading-6 text-slate-300">
                <ArrowUpRight className="mt-1 h-4 w-4 shrink-0 text-lime-300" />
                <span>{suggestion}</span>
              </li>
            ))}
          </ol>

          {closure.observedWeaknesses.length > 0 && (
            <div className="mt-7 rounded-xl border border-amber-300/20 bg-amber-300/[0.06] p-4">
              <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-amber-200">
                <ShieldCheck className="h-4 w-4" />
                仅基于已作答证据
              </div>
              <ul className="mt-3 space-y-2 text-xs leading-5 text-slate-300">
                {closure.observedWeaknesses.slice(0, 4).map(item => (
                  <li key={item}>— {item}</li>
                ))}
              </ul>
            </div>
          )}
        </aside>
      </div>

      {closure.keyEvidence.length > 0 && (
        <div className="border-t border-white/10 bg-black/20 px-6 py-5">
          <SectionTitle icon={Quote} label="证据索引" />
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            {closure.keyEvidence.slice(0, 4).map(evidence => (
              <div key={evidence.questionIndex} className="rounded-lg border border-white/10 px-4 py-3">
                <div className="flex items-center justify-between gap-3 text-xs">
                  <span className="truncate text-slate-400">
                    Q{evidence.questionIndex + 1} · {evidence.category}
                  </span>
                  <span className="font-mono font-bold text-lime-200">{evidence.observedScore}</span>
                </div>
                <p className="mt-2 line-clamp-2 text-xs leading-5 text-slate-300">
                  {evidence.evidenceSnippets[0] || evidence.question}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </motion.section>
  );
}

function Metric({ label, value, accent = false }: { label: string; value: number; accent?: boolean }) {
  return (
    <div className="bg-[#18201d] px-4 py-3">
      <div className={`font-mono text-xl font-black ${accent ? 'text-lime-300' : 'text-white'}`}>{value}</div>
      <div className="mt-0.5 text-[11px] text-slate-500">{label}</div>
    </div>
  );
}

function SectionTitle({ icon: Icon, label }: { icon: typeof ClipboardCheck; label: string }) {
  return (
    <div className="flex items-center gap-2 text-sm font-bold text-slate-100">
      <Icon className="h-4 w-4 text-lime-300" />
      <span>{label}</span>
    </div>
  );
}

function priorityTone(priority: string) {
  if (priority === 'HIGH') return 'bg-rose-400/15 text-rose-200';
  if (priority === 'MEDIUM') return 'bg-amber-300/15 text-amber-200';
  return 'bg-slate-500/20 text-slate-300';
}
