package com.juyan.shortlink.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juyan.shortlink.project.dao.entity.ShortLinkDO;
import com.juyan.shortlink.project.dto.req.RecycleBinSaveReqDTO;
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
}
