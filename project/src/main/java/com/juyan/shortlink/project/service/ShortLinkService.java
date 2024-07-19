package com.juyan.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.juyan.shortlink.project.dao.entity.ShortLinkDO;
import com.juyan.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.juyan.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.juyan.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.juyan.shortlink.project.dto.resp.ShortLinkPageRespDTO;

public interface ShortLinkService extends IService<ShortLinkDO> {
    /**
     * 新增短链接
     * @param requestParam
     * @return
     */
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);

    /**
     * 分页查询短链接
     * @param requestParam 分页查询短链接参数
     * @return
     */
    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam);
}
