package com.smartapply.ida.module.app.interview.service;

import com.smartapply.ida.common.domain.interview.vo.InterviewVoiceSummarizeTaskStatusVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 面试复盘语音总结异步任务：提交后立即返回 taskId，后台线程池跑「ASR 听写 + 大模型总结」全链路，
 * 客户端凭 taskId 轮询结果。
 * <p>
 * 异步化的动机：阿里云 NLS 听写按音频时长模拟实时发送（分钟级阻塞）+ LLM 总结（10~60s），
 * 同步执行会长时间独占 Tomcat 线程，高并发时拖垮整个 app 服务。
 */
public interface AppInterviewSummarizeTaskService {

    /**
     * 提交语音总结任务。若有录音文件，在请求线程内完成字节读取（MultipartFile 随请求结束失效），
     * 后续流水线在线程池线程中执行。
     *
     * @param file          录音文件，可为 null（纯文本模式）
     * @param rawTranscript 浏览器端转写文本，可为 null 或不足阈值（触发服务端 ASR）
     * @return taskId，供轮询接口使用
     */
    String submit(long userId, long sessionId, MultipartFile file, String rawTranscript) throws IOException;

    /**
     * 轮询任务状态；任务不存在、过期、不属于该用户或不属于该场次时抛业务异常。
     */
    InterviewVoiceSummarizeTaskStatusVO query(long userId, long sessionId, String taskId);
}
