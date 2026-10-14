package com.smartapply.ida.module.app.resume.service;

import com.smartapply.ida.common.domain.resume.vo.ResumeParseTaskStatusVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 简历解析异步任务：提交后立即返回taskId，后台线程池跑 [OSS + 抽文本 + 大模型] 全链路，
 * 客户端凭 taskId 轮询结果
 */
public interface AppResumeParseTaskService {
    /**
     * 提交解析任务。在请求线程内完成文件字节读取
     * 后续流水线在线程池线程中执行。
     * @return taskId，供轮询接口使用
     * @throws IOException
     */
    String submit(long userId, MultipartFile file) throws IOException;

    /**
     * 轮询任务状态：任务不存在、过期或不属于该用户时抛业务异常
     */
    ResumeParseTaskStatusVO query(long userId, String taskId);
}
