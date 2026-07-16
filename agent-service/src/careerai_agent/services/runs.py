from typing import cast
from uuid import uuid4

from langchain_core.runnables import RunnableConfig
from langgraph.graph.state import CompiledStateGraph

from careerai_agent.domain.models import AgentRun, CreateRunRequest, RunStatus
from careerai_agent.graph.state import CareerAgentState


class RunService:
    def __init__(
        self,
        graph: CompiledStateGraph[CareerAgentState, None, CareerAgentState, CareerAgentState],
    ) -> None:
        self._graph = graph

    async def create_run(self, request: CreateRunRequest, user_id: str) -> AgentRun:
        run_id = str(uuid4())
        initial_state: CareerAgentState = {
            "id": run_id,
            "user_id": user_id,
            "goal": request.goal,
            "constraints": request.constraints,
            "status": RunStatus.PLANNING,
            "plan": [],
            "artifacts": [],
            "errors": [],
            "pause_reason": None,
        }
        result = await self._graph.ainvoke(initial_state, self._config(run_id))
        return AgentRun.model_validate(result)

    async def get_run(self, run_id: str, user_id: str) -> AgentRun | None:
        snapshot = await self._graph.aget_state(self._config(run_id))
        values = cast(CareerAgentState, snapshot.values)
        if not values or values.get("user_id") != user_id:
            return None
        return AgentRun.model_validate(values)

    @staticmethod
    def _config(run_id: str) -> RunnableConfig:
        return {"configurable": {"thread_id": run_id}}
