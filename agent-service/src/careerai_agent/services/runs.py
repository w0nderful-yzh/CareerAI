from typing import cast
from uuid import uuid4

from langchain_core.runnables import RunnableConfig
from langgraph.graph.state import CompiledStateGraph

from careerai_agent.domain.models import AgentRun, CreateRunRequest, RunStatus
from careerai_agent.graph.state import CareerAgentState, GraphRuntimeContext


class RunService:
    """驱动准备 Agent，并把一次业务执行绑定到同一个 LangGraph checkpoint。"""

    def __init__(
        self,
        graph: CompiledStateGraph[
            CareerAgentState,
            GraphRuntimeContext,
            CareerAgentState,
            CareerAgentState,
        ],
    ) -> None:
        self._graph = graph

    async def create_run(
        self,
        request: CreateRunRequest,
        user_id: str,
        authorization: str,
    ) -> AgentRun:
        # run_id 同时是对外查询 ID 和 LangGraph thread_id，后续恢复必须复用它。
        run_id = str(uuid4())
        initial_state: CareerAgentState = {
            "id": run_id,
            "user_id": user_id,
            "goal": request.goal,
            "constraints": request.constraints,
            # Checkpoint 只保存基础类型，避免 LangGraph 严格序列化拒绝自定义枚举。
            "status": RunStatus.PLANNING.value,
            "plan": [],
            "artifacts": [],
            "errors": [],
            "pause_reason": None,
            "selected_resume_id": None,
            "job_id": None,
            "match_task_id": None,
            "match_report_id": None,
            "improvement_plan_id": None,
            "poll_count": 0,
        }
        result = await self._graph.ainvoke(
            initial_state,
            self._config(run_id),
            context=GraphRuntimeContext(authorization=authorization),
        )
        return AgentRun.model_validate(result)

    async def get_run(self, run_id: str, user_id: str) -> AgentRun | None:
        snapshot = await self._graph.aget_state(self._config(run_id))
        values = cast(CareerAgentState, snapshot.values)
        # checkpoint 本身不负责业务用户隔离，读取后仍要核对 state 中的 user_id。
        if not values or values.get("user_id") != user_id:
            return None
        return AgentRun.model_validate(values)

    async def resume_run(
        self,
        run_id: str,
        user_id: str,
        authorization: str,
    ) -> AgentRun | None:
        # 不创建新 State，而是从原快照重新进入 dispatch，由已有业务 ID 决定续跑节点。
        snapshot = await self._graph.aget_state(self._config(run_id))
        values = cast(CareerAgentState, snapshot.values)
        if not values or values.get("user_id") != user_id:
            return None
        result = await self._graph.ainvoke(
            values,
            self._config(run_id),
            context=GraphRuntimeContext(authorization=authorization),
        )
        return AgentRun.model_validate(result)

    @staticmethod
    def _config(run_id: str) -> RunnableConfig:
        return {"configurable": {"thread_id": run_id}}
