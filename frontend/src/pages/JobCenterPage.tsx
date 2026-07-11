import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  BriefcaseBusiness,
  Building2,
  ExternalLink,
  FileText,
  Loader2,
  MapPin,
  Play,
  Plus,
  Sparkles,
  Trash2,
} from 'lucide-react';
import { getErrorMessage } from '../api/request';
import { jobApi, type JobItem, type JobStatus } from '../api/jobs';
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

export default function JobCenterPage() {
  const navigate = useNavigate();
  const [jobs, setJobs] = useState<JobItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [error, setError] = useState('');

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
      setJobs(await jobApi.list());
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
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const startInterview = (job: JobItem) => {
    navigate('/interview', {
      state: {
        interviewConfig: {
          skillId: CUSTOM_SKILL_ID,
          difficulty: 'mid',
          questionCount: 6,
          jdText: job.jdText,
          customCategories: job.parsedCategories,
        },
      },
    });
  };

  return (
    <div className="mx-auto max-w-6xl">
      <div className="mb-8 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-primary-100 bg-primary-50 px-3 py-1 text-xs font-semibold text-primary-600 dark:border-primary-900/50 dark:bg-primary-900/20 dark:text-primary-300">
            <Sparkles className="h-3.5 w-3.5" />
            岗位驱动练习
          </div>
          <h1 className="flex items-center gap-3 text-2xl font-bold text-slate-800 dark:text-white">
            <BriefcaseBusiness className="h-7 w-7 text-primary-500" />
            岗位中心
          </h1>
          <p className="mt-1 text-slate-500 dark:text-slate-400">
            保存目标岗位，解析 JD 考察方向，并一键按岗位开始模拟面试。
          </p>
        </div>
        <div className="grid grid-cols-3 gap-3 text-center">
          <Metric label="目标岗位" value={activeJobs.length} />
          <Metric label="已投递" value={jobs.filter(job => job.status === 'APPLIED').length} />
          <Metric label="面试中" value={jobs.filter(job => job.status === 'INTERVIEW').length} />
        </div>
      </div>

      {error && (
        <div className="mb-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900/40 dark:bg-red-900/20 dark:text-red-200">
          {error}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[0.95fr_1.05fr]">
        <motion.form
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
              <h2 className="font-bold text-slate-900 dark:text-white">添加目标岗位</h2>
              <p className="text-xs text-slate-400">粘贴 JD 后先解析，再保存更稳。</p>
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
        </motion.form>

        <section className="rounded-[1.6rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <div className="mb-5 flex items-center justify-between">
            <h2 className="font-bold text-slate-900 dark:text-white">我的目标岗位</h2>
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

                  <div className="mt-4 flex gap-2">
                    <button
                      onClick={() => startInterview(job)}
                      disabled={job.parsedCategories.length === 0}
                      className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-primary-500 px-3 py-2 text-xs font-semibold text-white transition hover:bg-primary-600 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <Play className="h-3.5 w-3.5" />
                      按岗位面试
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
