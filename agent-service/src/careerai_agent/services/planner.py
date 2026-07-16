import json
from typing import Any, Protocol

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.output_parsers import PydanticOutputParser
from pydantic import BaseModel, Field

from careerai_agent.domain.models import PlanStep
from careerai_agent.models.factory import DynamicChatModelFactory


class PlanDraft(BaseModel):
    steps: list[str] = Field(min_length=5, max_length=5)


class AgentPlanner(Protocol):
    async def create_plan(self, goal: str, constraints: dict[str, Any]) -> list[PlanStep]: ...


class LangChainAgentPlanner:
    def __init__(self, model_factory: DynamicChatModelFactory) -> None:
        self._model_factory = model_factory
        self._parser = PydanticOutputParser(pydantic_object=PlanDraft)

    async def create_plan(self, goal: str, constraints: dict[str, Any]) -> list[PlanStep]:
        model = await self._model_factory.get_chat_model()
        response = await model.ainvoke(
            [
                SystemMessage(
                    content=(
                        "你是 CareerAI 的业务执行规划器。只规划可由后续业务工具完成的步骤，"
                        "不要声称已经执行，也不要输出对话建议。严格按以下五个阶段规划："
                        "读取简历和岗位、启动岗位匹配、读取匹配报告、决定准备策略、保存准备计划。"
                        f"\n{self._parser.get_format_instructions()}"
                    )
                ),
                HumanMessage(
                    content=(
                        f"目标：{goal}\n"
                        f"约束：{json.dumps(constraints, ensure_ascii=False, sort_keys=True)}"
                    )
                ),
            ]
        )
        content = response.content
        if not isinstance(content, str):
            raise ValueError("model returned non-text planning output")
        draft = self._parser.parse(content)
        return [
            PlanStep(id=f"plan_{index}", title=title.strip())
            for index, title in enumerate(draft.steps, start=1)
            if title.strip()
        ]
