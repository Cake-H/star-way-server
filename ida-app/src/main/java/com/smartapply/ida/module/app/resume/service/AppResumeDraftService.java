package com.smartapply.ida.module.app.resume.service;

import com.smartapply.ida.common.domain.resume.dto.ResumeSaveDraftRequest;
import com.smartapply.ida.common.domain.resume.dto.ResumeUpdateRequest;
import com.smartapply.ida.common.domain.resume.vo.ResumeSaveDraftResultVO;

/**
 * 简历草稿落库：写入 {@code ida_resume} 及教育/工作/项目等子表。
 * <p>
 * 实现类见 {@link com.smartapply.ida.module.app.resume.service.impl.AppResumeDraftServiceImpl}（含整份覆盖、默认简历、Bean 拷贝与日期转换等说明）。
 */
public interface AppResumeDraftService {

    /**
     * 新建或更新简历；实现侧为单事务，失败全回滚。
     *
     * @param userId  当前用户
     * @param request 草稿 JSON，结构见 {@link ResumeSaveDraftRequest}
     * @return 保存后的简历主键
     */
    ResumeSaveDraftResultVO saveDraft(long userId, ResumeSaveDraftRequest request);

    /**
     * 修改已有简历（REST {@code PUT} 专用）：主键由路径传入，与 {@link ResumeSaveDraftRequest#resumeId} 类请求体解耦，
     * 避免前端在「编辑页」重复携带 id。内部复用 {@link #saveDraft(long, ResumeSaveDraftRequest)} 的落库逻辑。
     */
    ResumeSaveDraftResultVO updateResume(long userId, long resumeId, ResumeUpdateRequest request);

    /**
     * 软删除简历（REST {@code DELETE}）：主表写入 {@code deleted_at}，子表行保留（便于审计或后续扩展「回收站」）。
     * <p>
     * 若删除的是当前用户的默认简历，则自动将「未删除且更新时间最新」的一份简历设为默认；若无其它简历则不再存在默认。
     *
     * @param userId   当前用户
     * @param resumeId 目标简历主键
     */
    void softDeleteResume(long userId, long resumeId);
}
