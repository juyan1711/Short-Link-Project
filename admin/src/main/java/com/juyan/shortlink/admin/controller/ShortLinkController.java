package com.juyan.shortlink.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.juyan.shortlink.admin.common.convention.result.Result;
import com.juyan.shortlink.admin.common.convention.result.Results;
import com.juyan.shortlink.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import com.juyan.shortlink.admin.remote.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.juyan.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.juyan.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.juyan.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import com.juyan.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.juyan.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 短链接后管控制层
 */
@RestController
public class ShortLinkController {
    //TODO:后续重构为SpringCloud调用
    ShortLinkActualRemoteService shortLinkActualRemoteService = new ShortLinkActualRemoteService() {
    };

    /**
     * 新增短链接
     */
    @PostMapping("/api/short-link/admin/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return shortLinkActualRemoteService.createShortLink(requestParam);
    }

    /**
     * 修改短链接
     * @param requestParam
     * @return
     */
    @PostMapping("/api/short-link/admin/v1/update")
    public Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam){
        shortLinkActualRemoteService.updateShortLink(requestParam);
        return Results.success();
    }


    /**
     * 分页查询短链接
     */
    @GetMapping("/api/short-link/admin/v1/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam){
        return shortLinkActualRemoteService.pageShortLink(requestParam);
    }

    /**
     * 查询短链接分组内数量
     */
    @GetMapping("/api/short-link/admin/v1/count")
    public Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupShortLinkCount(@RequestParam("requestParam") List<String> requestParam) {
        return shortLinkActualRemoteService.listGroupShortLinkCount(requestParam);
    }


}
