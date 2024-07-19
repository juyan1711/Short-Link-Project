package com.juyan.shortlink.project.controller;

import com.juyan.shortlink.project.common.convention.result.Result;
import com.juyan.shortlink.project.common.convention.result.Results;
import com.juyan.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.juyan.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.juyan.shortlink.project.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接控制层
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkController {
    private final ShortLinkService shortLinkService;

    @PostMapping("/api/short-link/v1/create")

    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return Results.success(shortLinkService.createShortLink(requestParam));
    }
}
