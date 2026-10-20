package com.smartapply.ida.module.plugin.jobApplication.service;

import com.smartapply.ida.common.domain.plugin.jobApplication.dto.PluginJobApplicationParseRequest;
import com.smartapply.ida.common.domain.plugin.jobApplication.vo.PluginJobApplicationParseResultVO;

/**
 * 插件投递记录业务：识别当前页面投递信息并入库（一页可能多条记录），
 * LLM 不可用时降级为仅存档网址。
 */
public interface PluginJobApplicationParseService {

    /**
     * 识别页面快照中的投递记录并落库。
     *
     * @param userId 当前登录用户
     * @param body   扩展采集的页面快照（pageUrl 必填，textContent 服务端截断）
     * @return 处理结果（含每条记录的新建/判重状态；parsed=false 表示走了降级存档）
     */
    PluginJobApplicationParseResultVO parseAndSave(Long userId, PluginJobApplicationParseRequest body);
}
