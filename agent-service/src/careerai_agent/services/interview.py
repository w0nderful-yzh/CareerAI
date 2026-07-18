import json
from collections.abc import AsyncIterator
from enum import StrEnum
from typing import Protocol

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.output_parsers import PydanticOutputParser
from pydantic import BaseModel, ConfigDict, Field, model_validator

from careerai_agent.models.config_client import ModelConfigError
from careerai_agent.models.factory import DynamicChatModelFactory
from careerai_agent.tools.models import (
    InterviewTurnContext,
    InterviewTurnEvaluation,
    NextQuestionIntent,
)


class InterviewAction(StrEnum):
    FOLLOW_UP = "FOLLOW_UP"
    SWITCH_TOPIC = "SWITCH_TOPIC"
    ADJUST_DIFFICULTY = "ADJUST_DIFFICULTY"
    END_INTERVIEW = "END_INTERVIEW"


class InterviewIntent(StrEnum):
    AUTO = "AUTO"
    ANSWER = "ANSWER"
    END = "END"
    SKIP = "SKIP"
    HINT = "HINT"
    EXPLAIN = "EXPLAIN"
    CONTINUE = "CONTINUE"


class DifficultyAdjustment(StrEnum):
    KEEP = "KEEP"
    HARDER = "HARDER"
    EASIER = "EASIER"


class InterviewEndReason(StrEnum):
    PLAN_COMPLETED = "PLAN_COMPLETED"
    QUESTION_LIMIT = "QUESTION_LIMIT"
    SUFFICIENT_EVIDENCE = "SUFFICIENT_EVIDENCE"
    LOW_INFORMATION = "LOW_INFORMATION"
    OFF_TOPIC = "OFF_TOPIC"


class InterviewDecision(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    action: InterviewAction
    rationale: str = Field(min_length=10, max_length=500)
    answer_score: int = Field(ge=0, le=100, alias="answerScore")
    feedback: str = Field(min_length=5, max_length=1000)
    difficulty_adjustment: DifficultyAdjustment = Field(alias="difficultyAdjustment")
    next_question_intent: NextQuestionIntent | None = Field(
        default=None,
        alias="nextQuestionIntent",
    )
    evaluation: InterviewTurnEvaluation
    end_reason: InterviewEndReason | None = Field(default=None, alias="endReason")

    @model_validator(mode="after")
    def validate_next_question_intent(self) -> "InterviewDecision":
        if self.action is InterviewAction.END_INTERVIEW:
            if self.next_question_intent is not None or self.end_reason is None:
                raise ValueError("结束面试时不能携带下一题意图，且必须说明结束原因")
            return self
        if self.next_question_intent is None or self.end_reason is not None:
            raise ValueError("继续面试时必须提供下一题意图，且不能携带结束原因")
        if self.action is InterviewAction.FOLLOW_UP and not self.next_question_intent.follow_up:
            raise ValueError("FOLLOW_UP 必须生成追问意图")
        if self.action is InterviewAction.SWITCH_TOPIC and self.next_question_intent.follow_up:
            raise ValueError("SWITCH_TOPIC 不能生成追问意图")
        if (
            self.action is InterviewAction.ADJUST_DIFFICULTY
            and self.difficulty_adjustment is DifficultyAdjustment.KEEP
        ):
            raise ValueError("ADJUST_DIFFICULTY 必须明确提高或降低难度")
        return self


class InterviewDecisionError(RuntimeError):
    pass


class InterviewDecisionMaker(Protocol):
    async def decide(self, context: InterviewTurnContext, answer: str) -> InterviewDecision: ...

    async def assist(self, context: InterviewTurnContext, intent: InterviewIntent) -> str: ...

    def assist_stream(
        self,
        context: InterviewTurnContext,
        intent: InterviewIntent,
    ) -> AsyncIterator[str]: ...


def resolve_interview_intent(requested: InterviewIntent, text: str) -> InterviewIntent:
    """显式按钮优先；AUTO 仅识别简短控制语句，避免误判正常技术回答。"""
    if requested is not InterviewIntent.AUTO:
        return requested
    normalized = "".join(text.strip().lower().split())
    if len(normalized) > 40:
        return InterviewIntent.ANSWER
    if any(value in normalized for value in ("结束面试", "不想面了", "不想继续面试", "提前结束")):
        return InterviewIntent.END
    if normalized in {"继续", "继续面试", "继续下一题"}:
        return InterviewIntent.CONTINUE
    if any(value in normalized for value in ("跳过", "下一题", "换一题")):
        return InterviewIntent.SKIP
    if any(value in normalized for value in ("给点提示", "提示一下", "需要提示")):
        return InterviewIntent.HINT
    if any(value in normalized for value in ("讲解一下", "不会这题", "直接讲答案", "告诉我答案")):
        return InterviewIntent.EXPLAIN
    return InterviewIntent.ANSWER


class LangChainInterviewDecisionMaker:
    """评价当前回答并选择下一题方向；不生成最终题目，也不直接推进 Java Session。"""

    def __init__(self, model_factory: DynamicChatModelFactory) -> None:
        self._model_factory = model_factory
        self._parser = PydanticOutputParser(pydantic_object=InterviewDecision)

    async def decide(self, context: InterviewTurnContext, answer: str) -> InterviewDecision:
        try:
            # 上下文中的蓝图、能力画像和 requirement ID 都来自 Java 业务快照。
            model = await self._model_factory.get_chat_model()
            response = await model.ainvoke(
                [
                    SystemMessage(
                        content=(
                            "你是 CareerAI 的自适应技术面试决策 Agent。先评价当前回答，再决定"
                            "下一题的结构化考察意图；不要生成最终问题文本。\n"
                            "评分必须基于准确性、深度、具体证据和表达完整度。\n"
                            "evaluation 必须分别评价适用的技术、项目、场景、排障、表达和岗位维度；"
                            "不适用维度填 null，不能用 0 分代替未观察。"
                            "evidenceSnippets 只能逐字引用"
                            "候选人当前回答中的短片段。\n"
                            "动作规则：FOLLOW_UP 的 nextQuestionIntent.followUp=true，且 "
                            "parentQuestionIndex"
                            "指向当前题或其主问题；SWITCH_TOPIC 必须 followUp=false；"
                            "ADJUST_DIFFICULTY 必须同时调整 difficulty。"
                            "非结束动作必须填写 nextQuestionIntent，END_INTERVIEW 必须为 null "
                            "并填写"
                            "endReason。questionType 只能从蓝图 questionTypes 中选择；"
                            "requirementId 只能来自蓝图 targetRequirementIds，蓝图为空时填 null。"
                            "优先验证 HIGH 重要度且未充分支撑的要求。"
                            "长期能力画像中：低分 STABLE 或 DECLINING 项应优先复测；"
                            "CONFLICT 项应换一种问法再验证；高分 STABLE 项避免重复基础题。"
                            "达到题目预算、已覆盖关键缺口或继续追问价值很低时结束。不得编造索引、"
                            "简历经历或 JD 事实。"
                            f"\n{self._parser.get_format_instructions()}"
                        )
                    ),
                    HumanMessage(
                        content=(
                            "面试业务上下文：\n"
                            f"{context.model_dump_json(by_alias=True)}\n"
                            "候选人当前回答：\n"
                            f"{json.dumps(answer, ensure_ascii=False)}"
                        )
                    ),
                ]
            )
            if not isinstance(response.content, str):
                raise InterviewDecisionError("model returned non-text interview decision")
            return self._parser.parse(response.content)
        except ModelConfigError:
            raise
        except InterviewDecisionError:
            raise
        except Exception as exc:
            raise InterviewDecisionError(f"面试决策生成失败：{exc}") from exc

    async def assist(self, context: InterviewTurnContext, intent: InterviewIntent) -> str:
        chunks = [chunk async for chunk in self.assist_stream(context, intent)]
        message = "".join(chunks).strip()
        if not message:
            raise InterviewDecisionError("model returned empty interview assistance")
        return message[:3000]

    async def assist_stream(
        self,
        context: InterviewTurnContext,
        intent: InterviewIntent,
    ) -> AsyncIterator[str]:
        # Hint/Explain 是即时辅助文本，不形成评分、能力观察或新的面试题。
        if intent not in {InterviewIntent.HINT, InterviewIntent.EXPLAIN}:
            raise InterviewDecisionError("unsupported interview assistance intent")
        instruction = (
            "只给 2 到 3 个递进提示，不直接给出完整答案"
            if intent is InterviewIntent.HINT
            else "给出简洁讲解，包含回答框架、关键点和一个贴近当前题目的示例"
        )
        try:
            model = await self._model_factory.get_chat_model()
            emitted = False
            async for chunk in model.astream(
                [
                    SystemMessage(
                        content=(
                            "你是技术面试陪练教练。必须围绕当前问题提供辅助，"
                            "不能虚构候选人的简历或 JD 经历。请使用简洁 Markdown 排版，"
                            "标题从三级标题开始，列表之间保留换行。" + instruction
                        )
                    ),
                    HumanMessage(
                        content=(
                            f"当前问题：{context.current_question.question}\n"
                            f"问题类别：{context.current_question.category}\n"
                            f"难度：{context.difficulty}"
                        )
                    ),
                ]
            ):
                text = chunk.content if isinstance(chunk.content, str) else ""
                if text:
                    emitted = True
                    yield text
            if not emitted:
                raise InterviewDecisionError("model returned empty interview assistance")
        except ModelConfigError:
            raise
        except InterviewDecisionError:
            raise
        except Exception as exc:
            raise InterviewDecisionError(f"面试辅助生成失败：{exc}") from exc
