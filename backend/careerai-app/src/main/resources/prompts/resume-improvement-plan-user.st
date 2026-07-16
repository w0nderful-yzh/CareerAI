请基于以下材料生成简历改进计划。

## 简历
文件名：{resumeFilename}

```text
{resumeText}
```

## 目标岗位
岗位：{jobTitle}
公司：{company}
地点：{location}

```text
{jdText}
```

{matchReport}

{agentDecision}

{latestInterviewReview}

请返回严格 JSON，字段含义如下：
- readinessScore：当前简历面向该岗位的准备度，0-100。
- summary：一句话总结当前最应该怎么改。
- priorityFixes：最高优先级修改点，3-5 条。
- resumeRewriteBullets：可直接写进简历的改写 bullet，3-5 条，尽量量化。
- projectUpgradeTasks：项目补强任务，3-5 条。
- interviewPracticeTasks：面试练习任务，3-5 条。
- learningTasks：学习/刷题/文档补齐任务，3-5 条。
- preparationTasks：结构化岗位准备任务，5-10 条。每项包含：
  - id：TASK-1 等稳定标识。
  - category：RESUME、PROJECT、LEARNING、INTERVIEW 之一。
  - title：具体可执行任务。
  - priority：P0、P1、P2 之一。
  - suggestedDays：建议完成天数，1-30。
  - verificationMethod：完成后如何验证，不接受“学习一下”这类模糊描述。
  - status：统一返回 PENDING。
  - relatedRequirementIds：关联证据矩阵中的 REQ id 数组。
