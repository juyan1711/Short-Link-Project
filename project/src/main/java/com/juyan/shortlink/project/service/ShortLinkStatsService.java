package com.juyan.shortlink.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juyan.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.juyan.shortlink.project.dao.mapper.LinkAccessStatsMapper;
import com.juyan.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.juyan.shortlink.project.dto.resp.ShortLinkStatsRespDTO;

/**
 * 短链接监控接口层
 */
public interface ShortLinkStatsService  {

    /**
     * 获取单个短链接监控数据
     *
     * @param requestParam 获取短链接监控数据入参
     * @return 短链接监控数据
     */
    ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam);

}
