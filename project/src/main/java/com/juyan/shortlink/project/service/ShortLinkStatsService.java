package com.juyan.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.juyan.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.juyan.shortlink.project.dao.mapper.LinkAccessStatsMapper;
import com.juyan.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.juyan.shortlink.project.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.juyan.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.juyan.shortlink.project.dto.resp.ShortLinkStatsAccessRecordRespDTO;
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

    /**
     * 访问单个短链接指定时间内访问记录监控数据
     * @param requestParam 获取短链接监控访问记录入参
     * @return 访问记录监控数据
     */
    IPage<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam);

    /**
     * 获取分组下所有短链接监控数据
     * @param requestParam 获取分组短链接监控数据入参
     * @return 分组短链接监控数据
     */
    ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam);
}
