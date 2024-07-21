package com.juyan.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juyan.shortlink.admin.dao.entity.GroupDO;
import com.juyan.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.juyan.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.juyan.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;

import java.util.List;

/**
 * 短链接分组接口层
 */
public interface GroupService extends IService<GroupDO> {
    /**
     * 新增短链接分组
     * @param username 用户名
     * @param groupName 短链接分组名
     */
    void saveGroup(String username,String groupName);

    /**
     * 新增短链接分组
     * @param groupName 短链接分组名
     */
    void saveGroup(String groupName);

    /**
     * 查询用户短链接分组集合
     *
     * @return 用户短链接分组集合
     */
    List<ShortLinkGroupRespDTO> listGroup();

    /**
     * 修改短链接分组
     *
     * @param requestParam 修改链接分组参数
     */
    void updateGroup(ShortLinkGroupUpdateReqDTO requestParam);

    /**
     * 删除短链接分组
     * @param gid 短链接分组参数
     */
    void deleteGroup(String gid);

    /**
     * 排序短链接分组
     */
    void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam);
}
