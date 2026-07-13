import { FormEvent, useMemo, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowRight, BriefcaseBusiness, Loader2, LockKeyhole, Sparkles, UserRound } from 'lucide-react';
import { getErrorMessage } from '../api/request';
import { useAuth } from '../context/AuthContext';

type AuthMode = 'login' | 'register';

export default function AuthPage() {
  const navigate = useNavigate();
  const { user, loading, login, register } = useAuth();
  const [mode, setMode] = useState<AuthMode>('login');
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const isRegister = mode === 'register';

  const title = useMemo(() => (
    isRegister ? '创建你的求职工作台' : '回到你的求职工作台'
  ), [isRegister]);

  if (!loading && user) {
    return <Navigate to="/history" replace />;
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');

    try {
      if (isRegister) {
        await register({ username, password, displayName });
      } else {
        await login({ username, password });
      }
      navigate('/history', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen overflow-hidden bg-[#0c1020] text-white">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(99,102,241,0.38),transparent_30%),radial-gradient(circle_at_82%_18%,rgba(14,165,233,0.26),transparent_28%),radial-gradient(circle_at_60%_88%,rgba(16,185,129,0.2),transparent_32%)]" />
      <div className="absolute inset-0 opacity-[0.08] [background-image:linear-gradient(rgba(255,255,255,.8)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.8)_1px,transparent_1px)] [background-size:48px_48px]" />

      <main className="relative z-10 grid min-h-screen lg:grid-cols-[1.08fr_0.92fr]">
        <section className="flex flex-col justify-between px-8 py-8 sm:px-12 lg:px-16">
          <div className="inline-flex w-fit items-center gap-3 rounded-full border border-white/10 bg-white/8 px-4 py-2 backdrop-blur-xl">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-white text-slate-950">
              <Sparkles className="h-4 w-4" />
            </span>
            <span className="text-sm font-semibold tracking-wide text-white/86">CareerAI 求职辅助台</span>
          </div>

          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.45 }}
            className="max-w-3xl py-16"
          >
            <div className="mb-8 inline-flex items-center gap-2 rounded-2xl border border-emerald-300/20 bg-emerald-300/10 px-4 py-2 text-sm text-emerald-100">
              <BriefcaseBusiness className="h-4 w-4" />
              从简历到岗位匹配，再到模拟面试
            </div>
            <h1 className="text-5xl font-black leading-[1.02] tracking-tight sm:text-6xl lg:text-7xl">
              把你的求职流程，
              <span className="block bg-gradient-to-r from-white via-indigo-100 to-sky-200 bg-clip-text text-transparent">
                收进一个清醒的系统。
              </span>
            </h1>
            <p className="mt-7 max-w-2xl text-lg leading-8 text-slate-300">
              登录后你的简历、知识库、面试记录都会按账号隔离。下一阶段我们就可以放心往“岗位中心 → JD 解析 → 匹配报告”继续加能力。
            </p>
          </motion.div>

          <div className="grid max-w-2xl grid-cols-3 gap-3 text-xs text-slate-400">
            {['Resume', 'JD Match', 'Mock Interview'].map((item) => (
              <div key={item} className="rounded-2xl border border-white/10 bg-white/5 px-4 py-3">
                {item}
              </div>
            ))}
          </div>
        </section>

        <section className="flex items-center justify-center px-6 py-10">
          <motion.div
            initial={{ opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.35, delay: 0.08 }}
            className="w-full max-w-md rounded-[2rem] border border-white/12 bg-white/[0.09] p-7 shadow-2xl shadow-black/30 backdrop-blur-2xl"
          >
            <div className="mb-7">
              <p className="text-sm font-medium text-sky-200">{isRegister ? 'NEW ACCOUNT' : 'WELCOME BACK'}</p>
              <h2 className="mt-2 text-2xl font-bold">{title}</h2>
              <p className="mt-2 text-sm text-slate-300">
                {isRegister ? '先建一个本地账号，后续数据会自动归属到你。' : '输入账号密码，继续整理你的求职材料。'}
              </p>
            </div>

            <form className="space-y-4" onSubmit={handleSubmit}>
              <label className="block">
                <span className="mb-2 flex items-center gap-2 text-sm text-slate-300">
                  <UserRound className="h-4 w-4" />
                  用户名
                </span>
                <input
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  className="w-full rounded-2xl border border-white/10 bg-slate-950/40 px-4 py-3 text-white outline-none transition focus:border-sky-300/70 focus:ring-4 focus:ring-sky-400/10"
                  placeholder="yzh666"
                  autoComplete="username"
                  required
                />
              </label>

              {isRegister && (
                <label className="block">
                  <span className="mb-2 block text-sm text-slate-300">昵称</span>
                  <input
                    value={displayName}
                    onChange={(event) => setDisplayName(event.target.value)}
                    className="w-full rounded-2xl border border-white/10 bg-slate-950/40 px-4 py-3 text-white outline-none transition focus:border-sky-300/70 focus:ring-4 focus:ring-sky-400/10"
                    placeholder="展示在侧边栏的名字"
                    autoComplete="name"
                  />
                </label>
              )}

              <label className="block">
                <span className="mb-2 flex items-center gap-2 text-sm text-slate-300">
                  <LockKeyhole className="h-4 w-4" />
                  密码
                </span>
                <input
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className="w-full rounded-2xl border border-white/10 bg-slate-950/40 px-4 py-3 text-white outline-none transition focus:border-sky-300/70 focus:ring-4 focus:ring-sky-400/10"
                  placeholder="至少 6 位"
                  type="password"
                  autoComplete={isRegister ? 'new-password' : 'current-password'}
                  required
                />
              </label>

              {error && (
                <div className="rounded-2xl border border-red-400/20 bg-red-500/10 px-4 py-3 text-sm text-red-100">
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={submitting}
                className="group mt-2 flex w-full items-center justify-center gap-2 rounded-2xl bg-white px-5 py-3.5 font-bold text-slate-950 transition hover:-translate-y-0.5 hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {submitting ? <Loader2 className="h-5 w-5 animate-spin" /> : null}
                {isRegister ? '创建账号' : '登录'}
                {!submitting && <ArrowRight className="h-5 w-5 transition group-hover:translate-x-1" />}
              </button>
            </form>

            <button
              type="button"
              onClick={() => {
                setMode(isRegister ? 'login' : 'register');
                setError('');
              }}
              className="mt-5 w-full text-center text-sm text-slate-300 hover:text-white"
            >
              {isRegister ? '已有账号？去登录' : '还没有账号？创建一个'}
            </button>
          </motion.div>
        </section>
      </main>
    </div>
  );
}
