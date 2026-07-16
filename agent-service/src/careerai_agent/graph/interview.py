from dataclasses import dataclass
from typing import TypedDict, cast

from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph
from langgraph.runtime import Runtime

from careerai_agent.services.interview import (
    InterviewDecision,
    InterviewDecisionMaker,
    InterviewIntent,
    resolve_interview_intent,
)
from careerai_agent.tools.business import build_business_tools, tool_result
from careerai_agent.tools.client import BusinessToolClient, ToolCallContext
from careerai_agent.tools.models import InterviewTurnContext, InterviewTurnResult


@dataclass(frozen=True)
class InterviewRuntimeContext:
    authorization: str
    run_id: str


class InterviewTurnState(TypedDict):
    session_id: str
    question_index: int
    answer: str
    context: InterviewTurnContext | None
    decision: InterviewDecision | None
    result: InterviewTurnResult | None


def build_interview_graph(
    decision_maker: InterviewDecisionMaker,
    business_client: BusinessToolClient,
) -> CompiledStateGraph[
    InterviewTurnState,
    InterviewRuntimeContext,
    InterviewTurnState,
    InterviewTurnState,
]:
    async def load_context(
        state: InterviewTurnState,
        runtime: Runtime[InterviewRuntimeContext],
    ) -> InterviewTurnState:
        tools = build_business_tools(
            business_client,
            _context(runtime, "load_interview_context"),
        )
        value = await tools.get_interview_turn_context.ainvoke({"session_id": state["session_id"]})
        return {**state, "context": tool_result(value, InterviewTurnContext)}

    async def decide(state: InterviewTurnState) -> InterviewTurnState:
        context = state["context"]
        if context is None:
            raise RuntimeError("interview context is missing")
        decision = await decision_maker.decide(context, state["answer"])
        return {**state, "decision": decision}

    async def apply(
        state: InterviewTurnState,
        runtime: Runtime[InterviewRuntimeContext],
    ) -> InterviewTurnState:
        decision = state["decision"]
        if decision is None:
            raise RuntimeError("interview decision is missing")
        tools = build_business_tools(
            business_client,
            _context(runtime, "apply_interview_decision"),
        )
        value = await tools.apply_interview_turn.ainvoke(
            {
                "session_id": state["session_id"],
                "question_index": state["question_index"],
                "answer": state["answer"],
                "action": decision.action.value,
                "rationale": decision.rationale,
                "answer_score": decision.answer_score,
                "feedback": decision.feedback,
                "difficulty_adjustment": decision.difficulty_adjustment.value,
                "next_question_intent": decision.next_question_intent,
                "evaluation": decision.evaluation,
                "end_reason": decision.end_reason.value if decision.end_reason else None,
            }
        )
        return {**state, "result": tool_result(value, InterviewTurnResult)}

    builder = StateGraph(InterviewTurnState, context_schema=InterviewRuntimeContext)
    builder.add_node("load_context", load_context)
    builder.add_node("decide", decide)
    builder.add_node("apply", apply)
    builder.add_edge(START, "load_context")
    builder.add_edge("load_context", "decide")
    builder.add_edge("decide", "apply")
    builder.add_edge("apply", END)
    return builder.compile()


def _context(
    runtime: Runtime[InterviewRuntimeContext],
    step_id: str,
) -> ToolCallContext:
    return ToolCallContext(
        authorization=runtime.context.authorization,
        run_id=runtime.context.run_id,
        step_id=step_id,
    )


class AdaptiveInterviewService:
    def __init__(
        self,
        graph: CompiledStateGraph[
            InterviewTurnState,
            InterviewRuntimeContext,
            InterviewTurnState,
            InterviewTurnState,
        ],
        decision_maker: InterviewDecisionMaker,
        business_client: BusinessToolClient,
    ) -> None:
        self._graph = graph
        self._decision_maker = decision_maker
        self._business_client = business_client

    async def submit_turn(
        self,
        session_id: str,
        question_index: int,
        answer: str,
        intent: InterviewIntent,
        authorization: str,
    ) -> InterviewTurnResult:
        resolved_intent = resolve_interview_intent(intent, answer)
        if resolved_intent in {InterviewIntent.HINT, InterviewIntent.EXPLAIN}:
            return await self._assist(
                session_id,
                question_index,
                resolved_intent,
                authorization,
            )
        if resolved_intent is not InterviewIntent.ANSWER:
            return await self._apply_control(
                session_id,
                question_index,
                resolved_intent,
                authorization,
            )
        initial: InterviewTurnState = {
            "session_id": session_id,
            "question_index": question_index,
            "answer": answer,
            "context": None,
            "decision": None,
            "result": None,
        }
        state = await self._graph.ainvoke(
            initial,
            context=InterviewRuntimeContext(
                authorization=authorization,
                run_id=f"interview:{session_id}:question:{question_index}",
            ),
        )
        result = cast(InterviewTurnState, state)["result"]
        if result is None:
            raise RuntimeError("interview graph completed without a result")
        return result.model_copy(update={"intent": InterviewIntent.ANSWER.value})

    async def _assist(
        self,
        session_id: str,
        question_index: int,
        intent: InterviewIntent,
        authorization: str,
    ) -> InterviewTurnResult:
        context = ToolCallContext(
            authorization=authorization,
            run_id=f"interview:{session_id}:question:{question_index}:{intent.value.lower()}",
            step_id="load_interview_context",
        )
        interview_context = await self._business_client.get_interview_turn_context(
            session_id,
            context,
        )
        if interview_context.current_question.question_index != question_index:
            raise RuntimeError("只能对当前面试问题请求辅助")
        message = await self._decision_maker.assist(interview_context, intent)
        return InterviewTurnResult(
            sessionId=session_id,
            completed=False,
            nextQuestion=interview_context.current_question,
            decision=None,
            answeredCount=interview_context.answered_count,
            totalQuestions=interview_context.total_questions,
            intent=intent.value,
            assistantMessage=message,
        )

    async def _apply_control(
        self,
        session_id: str,
        question_index: int,
        intent: InterviewIntent,
        authorization: str,
    ) -> InterviewTurnResult:
        context = ToolCallContext(
            authorization=authorization,
            run_id=f"interview:{session_id}:question:{question_index}:{intent.value.lower()}",
            step_id="apply_interview_control",
        )
        result = await self._business_client.apply_interview_turn(
            session_id,
            question_index,
            None,
            "END_INTERVIEW" if intent is InterviewIntent.END else "SWITCH_TOPIC",
            f"用户请求{intent.value}",
            0,
            "本轮未评分",
            "KEEP",
            None,
            None,
            "USER_REQUESTED" if intent is InterviewIntent.END else None,
            intent.value,
            context,
            idempotency_key=f"{context.run_id}:control",
        )
        return result.model_copy(update={"intent": intent.value})
