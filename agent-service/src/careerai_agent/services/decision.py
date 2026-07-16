import json
from enum import StrEnum
from typing import Annotated, Any, Protocol

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.output_parsers import PydanticOutputParser
from pydantic import BaseModel, ConfigDict, Field, StringConstraints

from careerai_agent.models.config_client import ModelConfigError
from careerai_agent.models.factory import DynamicChatModelFactory
from careerai_agent.tools.models import JobMatchReport

DecisionItem = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=300),
]


class PreparationAction(StrEnum):
    CREATE_IMPROVEMENT_PLAN = "CREATE_IMPROVEMENT_PLAN"
    COMPLETE_WITH_MATCH_REPORT = "COMPLETE_WITH_MATCH_REPORT"


class PreparationStrategy(StrEnum):
    RESUME_FIRST = "RESUME_FIRST"
    PROJECT_FIRST = "PROJECT_FIRST"
    INTERVIEW_FIRST = "INTERVIEW_FIRST"
    BALANCED = "BALANCED"


class PreparationDecision(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    action: PreparationAction
    strategy: PreparationStrategy
    rationale: str = Field(min_length=10, max_length=500)
    prioritized_gaps: list[DecisionItem] = Field(
        min_length=1,
        max_length=5,
        alias="prioritizedGaps",
    )
    supporting_evidence: list[DecisionItem] = Field(
        min_length=1,
        max_length=5,
        alias="supportingEvidence",
    )
    interview_focus: list[DecisionItem] = Field(
        min_length=1,
        max_length=5,
        alias="interviewFocus",
    )


class DecisionMakingError(RuntimeError):
    pass


class PreparationDecisionMaker(Protocol):
    async def decide(
        self,
        goal: str,
        constraints: dict[str, Any],
        report: JobMatchReport,
    ) -> PreparationDecision: ...


class LangChainPreparationDecisionMaker:
    def __init__(self, model_factory: DynamicChatModelFactory) -> None:
        self._model_factory = model_factory
        self._parser = PydanticOutputParser(pydantic_object=PreparationDecision)

    async def decide(
        self,
        goal: str,
        constraints: dict[str, Any],
        report: JobMatchReport,
    ) -> PreparationDecision:
        try:
            model = await self._model_factory.get_chat_model()
            response = await model.ainvoke(
                [
                    SystemMessage(
                        content=(
                            "你是 CareerAI 的求职准备决策 Agent。你的职责不是重复匹配报告，"
                            "而是依据证据选择下一步业务动作和准备策略。\n"
                            "当用户要求形成准备计划，且报告中存在可执行的差距或行动项时，"
                            "选择 CREATE_IMPROVEMENT_PLAN；只有用户明确只要分析结果时，"
                            "才选择 COMPLETE_WITH_MATCH_REPORT。\n"
                            "策略含义：RESUME_FIRST=先修简历证据，PROJECT_FIRST=先补项目，"
                            "INTERVIEW_FIRST=先训练表达与问答，BALANCED=多方向并行。\n"
                            "优先使用 evidenceMappings 中 HIGH 重要度且为 EXPRESSION_GAP、"
                            "EVIDENCE_GAP 或 CAPABILITY_GAP 的项目做决策。"
                            "所有结论必须能从报告中找到依据，不得虚构简历经历。"
                            f"\n{self._parser.get_format_instructions()}"
                        )
                    ),
                    HumanMessage(
                        content=(
                            f"用户目标：{goal}\n"
                            "执行约束："
                            f"{json.dumps(constraints, ensure_ascii=False, sort_keys=True)}\n"
                            "岗位匹配报告：\n"
                            f"{report.model_dump_json(by_alias=True)}"
                        )
                    ),
                ]
            )
            if not isinstance(response.content, str):
                raise DecisionMakingError("model returned non-text decision output")
            return self._parser.parse(response.content)
        except ModelConfigError:
            raise
        except DecisionMakingError:
            raise
        except Exception as exc:
            raise DecisionMakingError(f"Agent 决策生成失败：{exc}") from exc
