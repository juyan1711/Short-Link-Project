package com.juyan.shortlink.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juyan.shortlink.project.common.constant.RedisKeyConstant;
import com.juyan.shortlink.project.dao.entity.ShortLinkDO;
import com.juyan.shortlink.project.dao.mapper.ShortLinkMapper;
import com.juyan.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.juyan.shortlink.project.service.RecycleBinService;
import com.juyan.shortlink.project.toolkit.LinkUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 回收站管理接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements RecycleBinService {
    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public void saveRecycleBin(RecycleBinSaveReqDTO requestParam) {
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus,0)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO updateLink = ShortLinkDO.builder().enableStatus(1).build();
        baseMapper.update(updateLink,updateWrapper);
        stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY,requestParam.getFullShortUrl()));
    }
}
