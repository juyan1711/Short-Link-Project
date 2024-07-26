package com.juyan.shortlink.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.juyan.shortlink.admin.common.convention.result.Result;
import com.juyan.shortlink.admin.common.convention.result.Results;
import com.juyan.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.juyan.shortlink.admin.remote.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import com.juyan.shortlink.admin.remote.dto.req.ShortLinkGroupStatsReqDTO;
import com.juyan.shortlink.admin.remote.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.juyan.shortlink.admin.remote.dto.req.ShortLinkStatsReqDTO;
import com.juyan.shortlink.admin.remote.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.juyan.shortlink.admin.remote.dto.resp.ShortLinkStatsRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接监控控制层
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkStatsController {
    private final ShortLinkActualRemoteService shortLinkActualRemoteService = new ShortLinkActualRemoteService() {
    };

    /**
     * 访问单个短链接指定时间内监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats")
    public Result<ShortLinkStatsRespDTO> shortLinkStats(ShortLinkStatsReqDTO requestParam) {
        return shortLinkActualRemoteService.oneShortLinkStats(requestParam);
    }

    /**
     * 访问单个短链接指定时间内访问记录监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats/access-record")
    public Result<IPage<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam) {
        return shortLinkActualRemoteService.shortLinkStatsAccessRecord(requestParam);
    }

    /**
     * 访问分组短链接指定时间内监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats/group")
    public Result<ShortLinkStatsRespDTO> groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam) {
        return shortLinkActualRemoteService.groupShortLinkStats(requestParam);
    }
    /**
     * 访问分组短链接指定时间内访问记录监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats/access-record/group")
    public Result<IPage<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(ShortLinkGroupStatsAccessRecordReqDTO requestParam) {
        return shortLinkActualRemoteService.groupShortLinkStatsAccessRecord(requestParam);
    }
}
