import { motion } from 'framer-motion';
import { User } from 'lucide-react';
import type { ReactNode } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

export type InterviewMessageRole = 'interviewer' | 'user';

interface InterviewMessageBubbleProps {
  role: InterviewMessageRole;
  text: string;
  category?: string;
  highlight?: boolean;
  italic?: boolean;
  streaming?: boolean;
  suffix?: ReactNode;
}

export default function InterviewMessageBubble({
  role,
  text,
  category,
  highlight = false,
  italic = false,
  streaming = false,
  suffix,
}: InterviewMessageBubbleProps) {
  if (role === 'interviewer') {
    return (
      <motion.div
        initial={{ opacity: 0, x: -20 }}
        animate={{ opacity: 1, x: 0 }}
        className="flex items-start gap-3"
      >
        <div className="w-8 h-8 bg-primary-100 dark:bg-primary-900/50 rounded-full flex items-center justify-center flex-shrink-0">
          <User className="w-4 h-4 text-primary-600 dark:text-primary-400" />
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-sm font-semibold text-slate-700 dark:text-slate-300">面试官</span>
            {category && (
              <span className="px-2 py-0.5 bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 text-xs rounded-full">
                {category}
              </span>
            )}
          </div>
          <div
            className={`overflow-hidden rounded-2xl rounded-tl-none px-5 py-4 leading-relaxed ${
              highlight
                ? 'bg-slate-100 dark:bg-slate-700 border border-primary-300/60 dark:border-primary-700/40 text-slate-700 dark:text-slate-200'
                : 'border border-slate-200/80 bg-slate-50 text-slate-800 shadow-sm dark:border-slate-600/70 dark:bg-slate-700 dark:text-slate-100'
            } ${italic ? 'italic' : ''}`}
          >
            <InterviewMarkdown text={text} />
            {streaming && (
              <span className="ml-1 inline-block h-4 w-1.5 animate-pulse rounded-full bg-cyan-500 align-middle" />
            )}
            {suffix}
          </div>
        </div>
      </motion.div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="flex items-start gap-3 justify-end"
    >
      <div className="flex-1 max-w-[80%]">
        <div
          className={`whitespace-pre-wrap rounded-2xl rounded-tr-none p-4 leading-relaxed bg-primary-500 text-white ${
            highlight ? 'border border-primary-400/70 bg-primary-500/90' : ''
          } ${italic ? 'italic' : ''}`}
        >
          {text}
          {suffix}
        </div>
      </div>
      <div className="w-8 h-8 bg-slate-200 dark:bg-slate-600 rounded-full flex items-center justify-center flex-shrink-0">
        <svg className="w-4 h-4 text-slate-600 dark:text-slate-300" viewBox="0 0 24 24" fill="none">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="12" cy="7" r="4" stroke="currentColor" strokeWidth="2" />
        </svg>
      </div>
    </motion.div>
  );
}

function InterviewMarkdown({ text }: { text: string }) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        h1: ({ children }) => <h3 className="mb-3 mt-1 text-base font-bold text-slate-950 dark:text-white">{children}</h3>,
        h2: ({ children }) => <h3 className="mb-3 mt-1 text-base font-bold text-slate-950 dark:text-white">{children}</h3>,
        h3: ({ children }) => <h3 className="mb-2.5 mt-4 first:mt-0 text-[15px] font-bold text-slate-950 dark:text-white">{children}</h3>,
        p: ({ children }) => <p className="my-2 first:mt-0 last:mb-0 leading-7">{children}</p>,
        strong: ({ children }) => <strong className="font-semibold text-cyan-700 dark:text-cyan-300">{children}</strong>,
        ul: ({ children }) => <ul className="my-3 space-y-2 pl-5 marker:text-cyan-500 list-disc">{children}</ul>,
        ol: ({ children }) => <ol className="my-3 space-y-2 pl-5 marker:font-semibold marker:text-cyan-600 list-decimal dark:marker:text-cyan-400">{children}</ol>,
        li: ({ children }) => <li className="pl-1 leading-7">{children}</li>,
        blockquote: ({ children }) => (
          <blockquote className="my-3 border-l-2 border-cyan-500 bg-cyan-50/70 px-4 py-2 text-slate-700 dark:bg-cyan-950/25 dark:text-slate-200">
            {children}
          </blockquote>
        ),
        code: ({ children, className }) => className ? (
          <code className={`${className} text-sm`}>{children}</code>
        ) : (
          <code className="rounded bg-slate-200/80 px-1.5 py-0.5 font-mono text-[0.9em] text-cyan-800 dark:bg-slate-900/70 dark:text-cyan-200">
            {children}
          </code>
        ),
        pre: ({ children }) => (
          <pre className="my-3 overflow-x-auto rounded-xl border border-slate-700 bg-slate-950 p-4 font-mono text-sm leading-6 text-slate-100">
            {children}
          </pre>
        ),
        hr: () => <hr className="my-4 border-slate-200 dark:border-slate-600" />,
      }}
    >
      {text}
    </ReactMarkdown>
  );
}
