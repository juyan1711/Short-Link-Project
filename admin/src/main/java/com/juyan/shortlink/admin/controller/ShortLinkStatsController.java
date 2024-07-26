package com.juyan.shortlink.admin.controller;

import com.juyan.shortlink.admin.common.convention.result.Result;
import com.juyan.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.juyan.shortlink.admin.remote.dto.req.ShortLinkStatsReqDTO;
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
}
