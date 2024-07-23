package com.juyan.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.juyan.shortlink.project.dao.entity.ShortLinkDO;
import com.juyan.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.juyan.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.juyan.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.juyan.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import org.springframework.stereotype.Service;

/**
 * 回收站管理接口管理
 */
public interface RecycleBinService extends IService<ShortLinkDO> {
    /**
     * 保存回收站
     * @param requestParam
     */
    void saveRecycleBin(RecycleBinSaveReqDTO requestParam);

    /**
     * 分页查询回收站短链接
     * @param requestParam 分页查询短链接参数
     * @return
     */
    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam);
}
