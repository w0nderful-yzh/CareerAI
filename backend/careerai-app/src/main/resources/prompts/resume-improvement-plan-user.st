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

{latestInterviewReview}

请返回严格 JSON，字段含义如下：
- readinessScore：当前简历面向该岗位的准备度，0-100。
- summary：一句话总结当前最应该怎么改。
- priorityFixes：最高优先级修改点，3-5 条。
- resumeRewriteBullets：可直接写进简历的改写 bullet，3-5 条，尽量量化。
- projectUpgradeTasks：项目补强任务，3-5 条。
- interviewPracticeTasks：面试练习任务，3-5 条。
- learningTasks：学习/刷题/文档补齐任务，3-5 条。
