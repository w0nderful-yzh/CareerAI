import {useMemo, useRef} from 'react';
import {motion} from 'framer-motion';
import {Virtuoso, type VirtuosoHandle} from 'react-virtuoso';
import type {InterviewQuestion, InterviewSession} from '../types/interview';
import {BookOpen, BrainCircuit, FastForward, Lightbulb, Send, SkipForward} from 'lucide-react';
import InterviewMessageBubble from './InterviewMessageBubble';

interface Message {
  type: 'interviewer' | 'user';
  content: string;
  category?: string;
  questionIndex?: number;
  streamId?: string;
  streaming?: boolean;
}

interface InterviewChatPanelProps {
  session: InterviewSession;
  currentQuestion: InterviewQuestion | null;
  messages: Message[];
  answer: string;
  onAnswerChange: (answer: string) => void;
  onSubmit: () => void;
  onHint: () => void;
  onExplain: () => void;
  onSkip: () => void;
  onContinue: () => void;
  awaitingContinue: boolean;
  onCompleteEarly: () => void;
  isSubmitting: boolean;
  streamStatus: string;
  showCompleteConfirm: boolean;
  onShowCompleteConfirm: (show: boolean) => void;
}

/**
 * 面试聊天面板组件
 */
export default function InterviewChatPanel({
  session,
  currentQuestion,
  messages,
  answer,
  onAnswerChange,
  onSubmit,
  onHint,
  onExplain,
  onSkip,
  onContinue,
  awaitingContinue,
  // onCompleteEarly, // 暂时未使用
  isSubmitting,
  streamStatus,
  // showCompleteConfirm, // 暂时未使用
  onShowCompleteConfirm
}: InterviewChatPanelProps) {
  const virtuosoRef = useRef<VirtuosoHandle>(null);

  const progress = useMemo(() => {
    if (!session || !currentQuestion) return 0;
    return ((currentQuestion.questionIndex + 1) / session.totalQuestions) * 100;
  }, [session, currentQuestion]);

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
      onSubmit();
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-200px)] max-w-4xl mx-auto">
      {/* 进度条 */}
        <div
            className="bg-white dark:bg-slate-800 rounded-2xl p-6 mb-4 shadow-sm dark:shadow-slate-900/50 border border-slate-100 dark:border-slate-700">
        <div className="flex items-center justify-between mb-3">
          <span className="text-sm font-semibold text-slate-700 dark:text-slate-300">
            题目 {currentQuestion ? currentQuestion.questionIndex + 1 : 0} / {session.totalQuestions}
          </span>
            <span className="text-sm text-slate-500 dark:text-slate-400">
            {Math.round(progress)}%
          </span>
        </div>
            <div className="h-2 bg-slate-200 dark:bg-slate-700 rounded-full overflow-hidden">
          <motion.div
            className="h-full bg-gradient-to-r from-primary-500 to-primary-600 rounded-full"
            initial={{ width: 0 }}
            animate={{ width: `${progress}%` }}
            transition={{ duration: 0.3 }}
          />
        </div>
      </div>

      {/* 聊天区域 */}
        <div
            className="flex-1 bg-white dark:bg-slate-800 rounded-2xl shadow-sm dark:shadow-slate-900/50 overflow-hidden flex flex-col min-h-0 border border-slate-100 dark:border-slate-700">
        <Virtuoso
          ref={virtuosoRef}
          data={messages}
          initialTopMostItemIndex={messages.length - 1}
          followOutput="smooth"
          className="flex-1"
          itemContent={(_index, msg) => (
            <div className="pb-4 px-6 first:pt-6">
              <InterviewMessageBubble
                role={msg.type === 'interviewer' ? 'interviewer' : 'user'}
                text={msg.content}
                category={msg.category}
                streaming={msg.streaming}
              />
            </div>
          )}
        />

        {isSubmitting && streamStatus && (
          <motion.div
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            className="mx-6 mb-4 flex items-center gap-3 rounded-xl border border-cyan-200/70 bg-cyan-50/80 px-4 py-3 text-sm text-cyan-900 dark:border-cyan-800/60 dark:bg-cyan-950/25 dark:text-cyan-100"
          >
            <span className="relative flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-cyan-500/10">
              <BrainCircuit className="h-4 w-4 text-cyan-600 dark:text-cyan-300" />
              <span className="absolute -right-0.5 -top-0.5 h-2 w-2 animate-ping rounded-full bg-cyan-400" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-semibold">{streamStatus}</p>
              <div className="mt-1 flex gap-1" aria-hidden="true">
                {[0, 1, 2].map(index => (
                  <motion.span
                    key={index}
                    className="h-1 w-5 rounded-full bg-cyan-400/70"
                    animate={{ opacity: [0.25, 1, 0.25] }}
                    transition={{ duration: 1.1, repeat: Infinity, delay: index * 0.16 }}
                  />
                ))}
              </div>
            </div>
            <span className="text-[10px] font-bold uppercase tracking-[0.18em] text-cyan-600/70 dark:text-cyan-300/70">
              Live
            </span>
          </motion.div>
        )}

        {/* 输入区域 */}
            <div className="border-t border-slate-200 dark:border-slate-600 p-4 bg-slate-50 dark:bg-slate-700/50">
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <span className="mr-1 text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">
              Coach controls
            </span>
            <ControlButton icon={<Lightbulb className="h-3.5 w-3.5" />} label="给点提示" onClick={onHint} disabled={isSubmitting} />
            <ControlButton icon={<BookOpen className="h-3.5 w-3.5" />} label="讲解本题" onClick={onExplain} disabled={isSubmitting} />
            <ControlButton icon={<SkipForward className="h-3.5 w-3.5" />} label="跳过" onClick={onSkip} disabled={isSubmitting} />
            {awaitingContinue && (
              <button
                type="button"
                onClick={onContinue}
                disabled={isSubmitting}
                className="ml-auto inline-flex items-center gap-1.5 rounded-lg bg-cyan-600 px-3 py-1.5 text-xs font-bold text-white shadow-sm transition hover:bg-cyan-500 disabled:opacity-50"
              >
                <FastForward className="h-3.5 w-3.5" />
                继续下一题
              </button>
            )}
          </div>
          <div className="flex gap-3">
            <textarea
              value={answer}
              onChange={(e) => onAnswerChange(e.target.value)}
              onKeyDown={handleKeyPress}
              placeholder="输入你的回答... (Ctrl/Cmd + Enter 提交)"
              className="flex-1 px-4 py-3 border border-slate-300 dark:border-slate-500 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none bg-white dark:bg-slate-800 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500"
              rows={3}
              disabled={isSubmitting}
            />
            <div className="flex flex-col gap-2">
              <motion.button
                onClick={onSubmit}
                disabled={!answer.trim() || isSubmitting}
                className="px-6 py-3 bg-primary-500 text-white rounded-xl font-medium hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                whileHover={{ scale: isSubmitting || !answer.trim() ? 1 : 1.02 }}
                whileTap={{ scale: isSubmitting || !answer.trim() ? 1 : 0.98 }}
              >
                {isSubmitting ? (
                  <>
                    <motion.div
                      className="w-4 h-4 border-2 border-white border-t-transparent rounded-full"
                      animate={{ rotate: 360 }}
                      transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                    />
                    思考中
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    提交
                  </>
                )}
              </motion.button>
              <motion.button
                onClick={() => onShowCompleteConfirm(true)}
                disabled={isSubmitting}
                className="px-6 py-3 bg-slate-200 dark:bg-slate-600 text-slate-700 dark:text-slate-200 rounded-xl font-medium hover:bg-slate-300 dark:hover:bg-slate-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm"
                whileHover={{ scale: isSubmitting ? 1 : 1.02 }}
                whileTap={{ scale: isSubmitting ? 1 : 0.98 }}
              >
                提前交卷
              </motion.button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function ControlButton({
  icon,
  label,
  onClick,
  disabled,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  disabled: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:border-cyan-300 hover:text-cyan-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-300 dark:hover:border-cyan-700 dark:hover:text-cyan-300"
    >
      {icon}
      {label}
    </button>
  );
}
