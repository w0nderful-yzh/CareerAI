from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph

from careerai_agent.domain.models import RunStatus, StepStatus
from careerai_agent.graph.state import CareerAgentState
from careerai_agent.services.planner import AgentPlanner


def build_graph(
    checkpointer: BaseCheckpointSaver[str],
    planner: AgentPlanner,
) -> CompiledStateGraph[CareerAgentState, None, CareerAgentState, CareerAgentState]:
    async def initialize_plan(state: CareerAgentState) -> CareerAgentState:
        steps = await planner.create_plan(state["goal"], state["constraints"])
        return {
            **state,
            "status": RunStatus.PAUSED,
            "plan": [
                {
                    "id": step.id,
                    "title": step.title,
                    "status": StepStatus.PENDING,
                }
                for step in steps
            ],
            "pause_reason": "BUSINESS_TOOLS_NOT_CONFIGURED",
        }

    builder = StateGraph(CareerAgentState)
    builder.add_node("initialize_plan", initialize_plan)
    builder.add_edge(START, "initialize_plan")
    builder.add_edge("initialize_plan", END)
    return builder.compile(checkpointer=checkpointer)
