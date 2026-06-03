package com.smartapply.ida.ai.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartapply.ida.ai.volcengine.client.ArkChatCompletionClient;
import com.smartapply.ida.ai.volcengine.config.VolcengineArkProperties;
import com.smartapply.ida.common.domain.interview.InterviewSession;
import com.smartapply.ida.common.domain.interview.vo.InterviewQuestionDraftVO;
import com.smartapply.ida.common.domain.interview.vo.InterviewSessionMetaDraftVO;
import com.smartapply.ida.common.domain.interview.vo.InterviewVoiceSummarizeResultVO;
import com.smartapply.ida.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 调用 Chat Completions，将口述纯文本结构化为 {@link InterviewVoiceSummarizeResultVO}。
 * <p>
 * <b>注意：</b>本类只处理文字，不处理音频；听写由 {@link com.smartapply.ida.ai.speech.client.WhisperTranscriptionClient} 完成。
 * 配置项：{@code ida.volcengine-ark.interview-summarize}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSummarizeAiService {

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    /** 单字段写入库前的保护上限，避免异常长文本 */
    private static final int MAX_FIELD_CHARS = 8000;

    private final ArkChatCompletionClient arkChatCompletionClient;
    private final VolcengineArkProperties arkProperties;
    private final ObjectMapper objectMapper;

    /**
     * @param session   当前复盘场次（用于 User 消息中的已保存元信息对照）
     * @param transcript 已听写/转写完成的口述全文
     */
    public InterviewVoiceSummarizeResultVO summarize(InterviewSession session, String transcript) {
        if (!StringUtils.hasText(transcript)) {
            throw new BusinessException("无法识别语音，请重录或改用文字");
        }

        String apiKey = arkProperties.resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("未配置大模型 API Key，请设置环境变量 " + arkProperties.getEnvApiKey());
        }

        var cfg = arkProperties.getInterviewSummarize();
        String model = StringUtils.hasText(cfg.getModel())
                ? cfg.getModel().trim()
                : arkProperties.getResumeParse().getModel();

        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String userContent = InterviewSummarizePrompts.buildUserMessage(
                today,
                session.getCompanyName(),
                session.getPositionName(),
                session.getInterviewRound(),
                session.getInterviewAt() != null ? session.getInterviewAt().toString() : "",
                transcript.trim());

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", InterviewSummarizePrompts.SYSTEM.trim()),
                Map.of("role", "user", "content", userContent));

        Integer maxTokens = cfg.getMaxTokens() > 0 ? cfg.getMaxTokens() : null;

        JsonNode root;
        try {
            root = arkChatCompletionClient.chatCompletions(
                    apiKey, model, cfg.getTemperature(), messages,
                    cfg.getThinkingType(), cfg.getReasoningEffort(), maxTokens,
                    cfg.isResponseFormatJsonObject());
        } catch (ResourceAccessException e) {
            throw new BusinessException("调用大模型失败，请稍后重试：" + e.getMessage());
        } catch (HttpClientErrorException e) {
            throw translateHttpError(e);
        }

        if (root == null) {
            throw new BusinessException("大模型返回空响应");
        }
        JsonNode err = root.get("error");
        if (err != null && !err.isNull()) {
            throw new BusinessException("面试总结失败：" + err.path("message").asText("模型接口错误"));
        }

        String content = root.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new BusinessException("模型未返回可解析内容");
        }

        String json = stripMarkdownFence(content.trim());
        log.info("面试总结模型 JSON 长度={}, sessionId={}", json.length(), session.getId());

        try {
            JsonNode node = objectMapper.readTree(json);
            return sanitizeResult(node);
        } catch (JsonProcessingException e) {
            log.warn("面试总结 JSON 解析失败，前 200 字: {}", json.length() > 200 ? json.substring(0, 200) : json, e);
            throw new BusinessException("模型返回的 JSON 无法解析，请重试");
        }
    }

    /** 解析模型 JSON、校验日期格式、补全 suggestedTitle、过滤空 question 行。 */
    private InterviewVoiceSummarizeResultVO sanitizeResult(JsonNode node) throws JsonProcessingException {
        ObjectNode obj = node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();

        InterviewSessionMetaDraftVO meta = parseMeta(obj.path("extractedSession"));
        List<InterviewQuestionDraftVO> questions = parseQuestions(obj);

        String hint = obj.path("hint").asText("");
        if (!StringUtils.hasText(hint) && questions.isEmpty()) {
            hint = "未能从口述中识别出面试问答，请补充更多细节后重试";
        }

        InterviewVoiceSummarizeResultVO out = InterviewVoiceSummarizeResultVO.builder()
                .hint(StringUtils.hasText(hint) ? hint.trim() : null)
                .extractedSession(meta)
                .questions(questions)
                .build();
        syncLegacyTopLevelFields(out);
        return out;
    }

    private InterviewSessionMetaDraftVO parseMeta(JsonNode metaNode) {
        InterviewSessionMetaDraftVO meta;
        if (metaNode == null || metaNode.isMissingNode() || metaNode.isNull()) {
            meta = emptyMeta();
        } else {
            try {
                meta = objectMapper.treeToValue(metaNode, InterviewSessionMetaDraftVO.class);
            } catch (JsonProcessingException e) {
                meta = emptyMeta();
            }
        }
        if (meta == null) {
            meta = emptyMeta();
        }
        meta.setCompanyName(trimOrEmpty(meta.getCompanyName()));
        meta.setPositionName(trimOrEmpty(meta.getPositionName()));
        meta.setInterviewRound(trimOrEmpty(meta.getInterviewRound()));
        String at = trimOrEmpty(meta.getInterviewAt());
        if (StringUtils.hasText(at) && !DATE_PATTERN.matcher(at).matches()) {
            at = "";
        }
        meta.setInterviewAt(at);
        if (!StringUtils.hasText(meta.getSuggestedTitle())) {
            meta.setSuggestedTitle(buildSuggestedTitle(meta));
        } else {
            meta.setSuggestedTitle(meta.getSuggestedTitle().trim());
        }
        return meta;
    }

    private List<InterviewQuestionDraftVO> parseQuestions(ObjectNode obj) throws JsonProcessingException {
        JsonNode qNode = obj.get("questions");
        if (qNode == null || qNode.isNull() || !qNode.isArray()) {
            if (obj.has("question")) {
                InterviewQuestionDraftVO single = objectMapper.treeToValue(obj, InterviewQuestionDraftVO.class);
                return single != null && StringUtils.hasText(single.getQuestion())
                        ? List.of(sanitizeDraft(single))
                        : List.of();
            }
            return List.of();
        }
        List<InterviewQuestionDraftVO> list = new ArrayList<>();
        for (JsonNode item : qNode) {
            InterviewQuestionDraftVO d = objectMapper.treeToValue(item, InterviewQuestionDraftVO.class);
            if (d != null && StringUtils.hasText(d.getQuestion())) {
                list.add(sanitizeDraft(d));
            }
        }
        return list;
    }

    private InterviewQuestionDraftVO sanitizeDraft(InterviewQuestionDraftVO d) {
        d.setQuestion(truncate(d.getQuestion()));
        d.setMyAnswer(truncate(d.getMyAnswer()));
        d.setAnswerEvaluation(truncate(d.getAnswerEvaluation()));
        d.setReferenceAnswer(truncate(d.getReferenceAnswer()));
        d.setOtherNotes(truncate(d.getOtherNotes()));
        return d;
    }

    /** 将 {@code questions[0]} 同步到顶层五字段，兼容只读单题的前端。 */
    private static void syncLegacyTopLevelFields(InterviewVoiceSummarizeResultVO out) {
        if (out.getQuestions() == null || out.getQuestions().isEmpty()) {
            return;
        }
        InterviewQuestionDraftVO first = out.getQuestions().get(0);
        out.setQuestion(first.getQuestion());
        out.setMyAnswer(first.getMyAnswer());
        out.setAnswerEvaluation(first.getAnswerEvaluation());
        out.setReferenceAnswer(first.getReferenceAnswer());
        out.setOtherNotes(first.getOtherNotes());
    }

    private static InterviewSessionMetaDraftVO emptyMeta() {
        return InterviewSessionMetaDraftVO.builder()
                .companyName("")
                .positionName("")
                .interviewRound("")
                .interviewAt("")
                .suggestedTitle("")
                .build();
    }

    private static String buildSuggestedTitle(InterviewSessionMetaDraftVO meta) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(meta.getCompanyName())) {
            parts.add(meta.getCompanyName().trim());
        }
        if (StringUtils.hasText(meta.getPositionName())) {
            parts.add(meta.getPositionName().trim());
        }
        if (StringUtils.hasText(meta.getInterviewRound())) {
            parts.add(meta.getInterviewRound().trim());
        }
        return String.join(" · ", parts);
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= MAX_FIELD_CHARS ? t : t.substring(0, MAX_FIELD_CHARS);
    }

    private static String stripMarkdownFence(String raw) {
        String s = raw;
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int end = s.lastIndexOf("```");
            if (end > 0) {
                s = s.substring(0, end);
            }
        }
        return s.trim();
    }

    private static BusinessException translateHttpError(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        if (e.getStatusCode().value() == 402 || (body != null && body.contains("Insufficient Balance"))) {
            return new BusinessException("模型账户余额不足，请充值后重试");
        }
        return new BusinessException("调用大模型失败：" + (body != null && !body.isBlank() ? body : e.getMessage()));
    }
}
