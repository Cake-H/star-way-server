# createMockInterview 接口实现指南（Langchain4j 版）

> 目标：补全 `POST /api/v1/app/mock-interview` 接口，让 LLM 根据简历内容生成模拟面试题目并落库。
>
> 原则：遵循项目现有分层与命名风格，不破坏已有功能。

---

## 一、前置了解

### 1.1 项目模块分工

| 模块 | 职责 | 本次改动范围 |
|---|---|---|
| `ida-common` | 公共 DTO/VO/错误码/常量 | 新增 AI 结构化输出 VO |
| `ida-ai` | Langchain4j 底座：配置、Assistant、PromptProvider | 新增配置、Assistant、PromptProvider |
| `ida-app` | 正式端业务：Controller/Service/Mapper/AI 业务封装 | 新增业务 AI Service、补全 ServiceImpl |
| `ida-app-server` | 正式端 Spring Boot 启动壳 | 仅改 `application.yml` |
| `ida-plugin-server` | 插件端 Spring Boot 启动壳 | 仅改 `application.yml` |

### 1.2 现有 LLM 链路模式

项目已用 **Langchain4j 声明式 AI Service**：

```
Controller → ServiceImpl → 业务 AI Service (ida-app/ai/*)
                                  ↓
                         Assistant 接口 (ida-ai)
                                  ↓
                         PromptProvider (ida-ai)
                                  ↓
                         ChatModel Bean (ida-ai 自动配置)
```

参考案例：`InterviewSummarizeAiService` → `InterviewSummarizeAssistant` → `InterviewSummarizePromptProvider`。

### 1.3 已存在的模拟面试半成品

- Controller：`ida-app/.../mockInterview/controller/MockInterviewController.java`
- Service 接口：`ida-app/.../mockInterview/service/MockInterviewService.java`
- Service 实现：`ida-app/.../mockInterview/service/impl/MockInterviewServiceImpl.java`
- DTO：`ida-common/.../mockInterview/dto/MockInterviewCreateDTO.java`
- 实体：`MockInterviewSession`、`MockInterviewQuestion`
- Mapper：`MockInterviewSessionMapper`、`MockInterviewQuestionMapper`
- 表：`database/mysql/ida_v4_mock_interview.sql`

目前 `createMockInterview` 只做了简历归属校验，直接返回 `0L`。

---

## 二、实现步骤

### 步骤 1：新增「模拟面试题目生成」的 LLM 配置

#### 1.1 修改 `ida-ai/src/main/java/com/smartapply/ida/ai/langchain4j/config/LangChain4jProperties.java`

在 `interviewSummarize` 字段下面新增：

```java
/**
 * 模拟面试题目生成配置。
 */
private ModelConfig mockInterview = new ModelConfig();
```

#### 1.2 修改 `ida-ai/src/main/java/com/smartapply/ida/ai/langchain4j/config/LangChain4jAutoConfiguration.java`

新增 ChatModel Bean：

```java
@Bean("mockInterviewChatModel")
@ConditionalOnMissingBean(name = "mockInterviewChatModel")
public OpenAiChatModel mockInterviewChatModel() {
    return buildChatModel(properties.getMockInterview(), "模拟面试题目生成");
}
```

新增 AI Service Bean：

```java
@Bean
@ConditionalOnMissingBean
public MockInterviewGenerateAssistant mockInterviewGenerateAssistant(
        @Qualifier("mockInterviewChatModel") OpenAiChatModel chatModel) {
    log.info("[LangChain4j] 创建 MockInterviewGenerateAssistant");
    return AiServices.builder(MockInterviewGenerateAssistant.class)
            .chatLanguageModel(chatModel)
            .build();
}
```

并在文件顶部加入 import：

```java
import com.smartapply.ida.ai.langchain4j.assistant.MockInterviewGenerateAssistant;
```

> 注：`MockInterviewGenerateAssistant` 在步骤 3 中创建，此处可以先写，编译会在步骤 3 后通过。

#### 1.3 修改 `ida-app-server/src/main/resources/application.yml`

在 `ida.langchain4j` 下新增：

```yaml
      mock-interview:
        model: deepseek-v4-flash
        temperature: 0.4
        max-tokens: 8192
        read-timeout-ms: 300000
        response-format-json-object: true
```

#### 1.4 修改 `ida-plugin-server/src/main/resources/application.yml`

同样新增一段，保证 `ida-ai` 自动配置在两个 server 都能拿到配置：

```yaml
      mock-interview:
        model: deepseek-v4-flash
        temperature: 0.4
        max-tokens: 8192
        read-timeout-ms: 300000
        response-format-json-object: true
```

---

### 步骤 2：定义 AI 结构化输出 VO

Langchain4j 的 `AiServices` 会把模型返回的 JSON 直接反序列化到强类型 Bean。

#### 2.1 新建文件

`ida-common/src/main/java/com/smartapply/ida/common/domain/mockInterview/vo/MockInterviewGenerateResultVO.java`

```java
package com.smartapply.ida.common.domain.mockInterview.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewGenerateResultVO {

    @Builder.Default
    private List<MockInterviewQuestionDraftVO> questions = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockInterviewQuestionDraftVO {
        private String question;
        private String referenceAnswer;
        private String answerEvaluation;
        private String otherNotes;
    }
}
```

> 内层字段与 `ida_mock_interview_question` 表字段保持一致，方便后续落库拷贝。

---

### 步骤 3：创建 AI Service 接口（Assistant）

#### 3.1 新建文件

`ida-ai/src/main/java/com/smartapply/ida/ai/langchain4j/assistant/MockInterviewGenerateAssistant.java`

```java
package com.smartapply.ida.ai.langchain4j.assistant;

import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewGenerateResultVO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 模拟面试题目生成 AI Service。
 * <p>
 * 输入简历上下文，输出结构化面试题列表。
 */
public interface MockInterviewGenerateAssistant {

    /**
     * 根据简历上下文生成模拟面试题目。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息（含简历上下文）
     * @return 结构化题目列表
     */
    @SystemMessage("{{systemPrompt}}")
    MockInterviewGenerateResultVO generateQuestions(
            @V("systemPrompt") String systemPrompt,
            @UserMessage String userMessage
    );
}
```

---

### 步骤 4：创建 Prompt Provider

#### 4.1 新建文件

`ida-ai/src/main/java/com/smartapply/ida/ai/langchain4j/prompt/MockInterviewPromptProvider.java`

```java
package com.smartapply.ida.ai.langchain4j.prompt;

import org.springframework.stereotype.Component;

/**
 * 模拟面试题目生成提示词 Provider。
 */
@Component
public class MockInterviewPromptProvider {

    public static final String SYSTEM = """
            你是资深技术面试官。请根据下面提供的求职者简历信息，生成一场模拟面试中面试官可能提出的问题。
            问题应围绕简历中的教育背景、工作经历、项目经验、技能栈、个人优势等展开，避免编造简历中未出现的内容。
            每道题需给出：问题本身、参考答案/答题要点、评价维度、补充说明。
            输出必须是单个 JSON 对象，不要 Markdown，不要代码围栏，不要注释。
            若简历信息过少无法生成题目，questions 可为空数组。

            输出 JSON 结构（camelCase）：
            {
              "questions": [
                {
                  "question": "",
                  "referenceAnswer": "",
                  "answerEvaluation": "",
                  "otherNotes": ""
                }
              ]
            }
            """;

    /**
     * 构建用户消息。
     *
     * @param resumeContext 简历上下文（建议为 JSON 或结构化的可读文本）
     */
    public String buildUserMessage(String resumeContext) {
        return """
                【求职者简历信息】
                %s

                请根据以上简历生成模拟面试题目，只输出 JSON：
                """.formatted(resumeContext);
    }
}
```

---

### 步骤 5：创建业务层 AI 封装 Service

项目习惯在 `ida-app` 下再包一层 `com.smartapply.ida.ai.*` 做业务封装。

#### 5.1 新建文件

`ida-app/src/main/java/com/smartapply/ida/ai/mockInterview/MockInterviewGenerateAiService.java`

```java
package com.smartapply.ida.ai.mockInterview;

import com.smartapply.ida.ai.langchain4j.assistant.MockInterviewGenerateAssistant;
import com.smartapply.ida.ai.langchain4j.prompt.MockInterviewPromptProvider;
import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewGenerateResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 模拟面试题目生成业务封装。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewGenerateAiService {

    private final MockInterviewGenerateAssistant assistant;
    private final MockInterviewPromptProvider promptProvider;

    public MockInterviewGenerateResultVO generateQuestions(String resumeContext) {
        String userMessage = promptProvider.buildUserMessage(resumeContext);
        return assistant.generateQuestions(MockInterviewPromptProvider.SYSTEM, userMessage);
    }
}
```

---

### 步骤 6：实现 `MockInterviewServiceImpl.createMockInterview`

#### 6.1 修改文件

`ida-app/src/main/java/com/smartapply/ida/module/app/mockInterview/service/impl/MockInterviewServiceImpl.java`

#### 6.2 新增 import

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.ai.mockInterview.MockInterviewGenerateAiService;
import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewGenerateResultVO;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import com.smartapply.ida.common.domain.resume.vo.ResumeDetailVO;
import com.smartapply.ida.module.app.resume.service.AppResumeQueryService;
```

#### 6.3 新增依赖字段

在已有字段后面加入：

```java
private final AppResumeQueryService appResumeQueryService;
private final MockInterviewGenerateAiService mockInterviewGenerateAiService;
private final ObjectMapper objectMapper;
```

类上已有 `@AllArgsConstructor`，这三个字段会被自动注入。

#### 6.4 重写 `createMockInterview` 方法

把原来 `return 0L;` 替换为：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long createMockInterview(Long userId, MockInterviewCreateDTO dto) {
    // 校验 resumeId 是否是当前用户的（保留原有逻辑）
    Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
            .eq(Resume::getId, dto.getResumeId())
            .eq(Resume::getUserId, userId));
    if (resume == null) {
        throw new RuntimeException("简历不存在或无权访问");
    }

    // 1. 拉取完整简历（主表 + 9 张子表）
    ResumeDetailVO resumeDetail = appResumeQueryService.getResumeDetail(userId, dto.getResumeId());

    // 2. 把简历草稿序列化为文本上下文
    String resumeContext = serializeResumeDraft(resumeDetail);

    // 3. 调用 LLM 生成题目
    MockInterviewGenerateResultVO generated = mockInterviewGenerateAiService.generateQuestions(resumeContext);
    List<MockInterviewGenerateResultVO.MockInterviewQuestionDraftVO> drafts =
            generated.getQuestions() == null ? new ArrayList<>() : generated.getQuestions();

    // 4. 落库模拟面试场次
    LocalDateTime now = LocalDateTime.now();
    MockInterviewSession session = new MockInterviewSession();
    session.setUserId(userId);
    session.setResumeId(dto.getResumeId());
    session.setTitle(resolveTitle(dto, resumeDetail));
    session.setStatus("GENERATED");
    session.setQuestionCount(drafts.size());
    session.setCurrentQuestionId(null);
    session.setAnswerRevealed(0);
    session.setCreatedAt(now);
    session.setUpdatedAt(now);
    mockInterviewSessionMapper.insert(session);
    Long sessionId = session.getId();
    if (sessionId == null) {
        throw new RuntimeException("创建模拟面试失败");
    }

    // 5. 落库题目
    for (int i = 0; i < drafts.size(); i++) {
        MockInterviewGenerateResultVO.MockInterviewQuestionDraftVO d = drafts.get(i);
        MockInterviewQuestion q = new MockInterviewQuestion();
        q.setSessionId(sessionId);
        q.setSortOrder(i + 1);
        q.setQuestion(d.getQuestion());
        q.setReferenceAnswer(d.getReferenceAnswer());
        q.setAnswerEvaluation(d.getAnswerEvaluation());
        q.setOtherNotes(d.getOtherNotes());
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        mockInterviewQuestionMapper.insert(q);
    }

    return sessionId;
}
```

#### 6.5 新增两个私有方法

```java
/**
 * 把简历详情中的结构化草稿序列化为 JSON，作为 LLM 的输入上下文。
 * 序列化失败时降级为空对象，避免整单失败。
 */
private String serializeResumeDraft(ResumeDetailVO detail) {
    try {
        ResumeParseDraftDto draft = detail.getDraft();
        if (draft == null) {
            return "{}";
        }
        return objectMapper.writeValueAsString(draft);
    } catch (Exception e) {
        log.warn("简历草稿序列化失败，将使用空上下文", e);
        return "{}";
    }
}

/**
 * 标题解析顺序：请求传入 > 期望岗位 + "模拟面试" > 姓名 + "模拟面试" > 默认文案。
 */
private String resolveTitle(MockInterviewCreateDTO dto, ResumeDetailVO detail) {
    if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
        return dto.getTitle().trim();
    }
    ResumeParseDraftDto draft = detail.getDraft();
    if (draft != null && draft.getExpectedPositions() != null && !draft.getExpectedPositions().isBlank()) {
        return draft.getExpectedPositions().trim() + " · 模拟面试";
    }
    if (draft != null && draft.getFullName() != null && !draft.getFullName().isBlank()) {
        return draft.getFullName().trim() + " · 模拟面试";
    }
    return "模拟面试";
}
```

---

### 步骤 7：编译启动验证

#### 7.1 编译

```bash
mvn clean compile -pl ida-app-server -am -DskipTests
```

#### 7.2 启动

```bash
mvn -pl ida-app-server spring-boot:run
```

#### 7.3 接口测试

启动后访问 Swagger UI（默认）：

```
http://localhost:8088/swagger-ui.html
```

找到 `POST /api/v1/app/mock-interview`，用已保存的 `resumeId` 调用，成功后应返回模拟面试 `sessionId`。

---

## 三、常见问题与后续优化建议

### 3.1 简历上下文怎么选？

当前实现复用 `AppResumeQueryService.getResumeDetail()`，把 `ResumeParseDraftDto` 序列化为 JSON 送入模型。优点：
- 不需要再从 OSS 下载原始简历文件；
- 结构清晰，LLM 容易识别各模块。

如果后续想更贴近原始简历，可以在 `extra_json` 里取出 `lastUpload.ossObjectKey` 下载原件再抽文本。

### 3.2 题目数量与质量

当前 Prompt 没有限制题目数量，完全由模型决定。如需固定数量，可在 `MockInterviewCreateDTO` 增加 `questionCount` 字段，并在 Prompt 里加入 `"请生成约 N 道题目"`。

### 3.3 异常处理

当前 `createMockInterview` 对 LLM 调用异常未做特殊降级。如果希望 LLM 失败时给用户友好提示，可以在 `MockInterviewGenerateAiService` 里 catch 异常并转换为 `BusinessException`。

### 3.4 事务边界

`@Transactional(rollbackFor = Exception.class)` 保证了 session 和 questions 一起成功或回滚。如果后续把 LLM 调用改成异步任务，需要拆事务并增加任务状态表。

---

## 四、文件改动清单

| 类型 | 文件路径 |
|---|---|
| 修改 | `ida-ai/src/main/java/com/smartapply/ida/ai/langchain4j/config/LangChain4jProperties.java` |
| 修改 | `ida-ai/src/main/java/com/smartapply/ida/ai/langchain4j/config/LangChain4jAutoConfiguration.java` |
| 修改 | `ida-app-server/src/main/resources/application.yml` |
| 修改 | `ida-plugin-server/src/main/resources/application.yml` |
| 修改 | `ida-app/src/main/java/com/smartapply/ida/module/app/mockInterview/service/impl/MockInterviewServiceImpl.java` |
| 新建 | `ida-common/src/main/java/com/smartapply/ida/common/domain/mockInterview/vo/MockInterviewGenerateResultVO.java` |
| 新建 | `ida-ai/src/main/java/com/smartapply/ida/ai/langchain4j/assistant/MockInterviewGenerateAssistant.java` |
| 新建 | `ida-ai/src/main/java/com/smartapply/ida/ai/langchain4j/prompt/MockInterviewPromptProvider.java` |
| 新建 | `ida-app/src/main/java/com/smartapply/ida/ai/mockInterview/MockInterviewGenerateAiService.java` |
