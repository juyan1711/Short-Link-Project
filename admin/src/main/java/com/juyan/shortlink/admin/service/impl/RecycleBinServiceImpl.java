package com.juyan.shortlink.admin.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juyan.shortlink.admin.common.biz.user.UserContext;
import com.juyan.shortlink.admin.common.convention.exception.ClientException;
import com.juyan.shortlink.admin.common.convention.result.Result;
import com.juyan.shortlink.admin.dao.entity.GroupDO;
import com.juyan.shortlink.admin.dao.mapper.GroupMapper;
import com.juyan.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.juyan.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.juyan.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.juyan.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service(value = "recycleBinServiceImplByAdmin")
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {
    private final GroupMapper groupMapper;
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    @Override
    public Result<Page<ShortLinkPageRespDTO>> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, 0);
        List<GroupDO> groupDoList = groupMapper.selectList(queryWrapper);
        if(CollUtil.isEmpty(groupDoList)){
            throw new ClientException("用户无分组信息");
        }
        requestParam.setGidList(groupDoList.stream().map(GroupDO::getGid).toList());
        return shortLinkActualRemoteService.pageRecycleBinShortLink(requestParam.getGidList(), requestParam.getCurrent(), requestParam.getSize());
    }
}
