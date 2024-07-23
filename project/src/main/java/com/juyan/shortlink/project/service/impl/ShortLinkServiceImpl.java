package com.juyan.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juyan.shortlink.project.common.constant.RedisKeyConstant;
import com.juyan.shortlink.project.common.convention.exception.ClientException;
import com.juyan.shortlink.project.common.convention.exception.ServiceException;
import com.juyan.shortlink.project.common.convention.result.Result;
import com.juyan.shortlink.project.common.enums.VailDateTypeEnum;
import com.juyan.shortlink.project.config.RBloomFilterConfiguration;
import com.juyan.shortlink.project.dao.entity.ShortLinkDO;
import com.juyan.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.juyan.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.juyan.shortlink.project.dao.mapper.ShortLinkMapper;
import com.juyan.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.juyan.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.juyan.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.juyan.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.juyan.shortlink.project.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.juyan.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.juyan.shortlink.project.service.ShortLinkService;
import com.juyan.shortlink.project.toolkit.HashUtil;
import com.juyan.shortlink.project.toolkit.LinkUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 短链接接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {
    /**
     * 布隆过滤器
     */
    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    /**
     * 路由表
     */
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    /**
     * Redis缓存
     */
    private final StringRedisTemplate stringRedisTemplate;
    /**
     * 分布式锁
     */
    private final RedissonClient redissonClient;
    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        String shortLinkSuffix = generateSuffix(requestParam);
        String fullShortUrl = requestParam.getDomain()+"/"+shortLinkSuffix;

        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(requestParam.getDomain())
                .shortUri(shortLinkSuffix)
                .fullShortUrl(fullShortUrl)
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .enableStatus(0)
                .createdType(requestParam.getCreatedType())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .favicon(getFavicon(requestParam.getOriginUrl()))
                .describe(requestParam.getDescribe())
                .build();

        ShortLinkGotoDO shortLinkGotoDO = ShortLinkGotoDO.builder()
                .gid(requestParam.getGid())
                .fullShortUrl(fullShortUrl)
                .build();

        try{
            baseMapper.insert(shortLinkDO);
            shortLinkGotoMapper.insert(shortLinkGotoDO);
        }catch (DuplicateKeyException ex){
            log.warn("短链接: {} 重复入库",fullShortUrl);
            throw new ServiceException("短链接重复生成");
        }
        shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
        //缓存预热，并且设置短链接的过期时间
        stringRedisTemplate.opsForValue().set(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY,fullShortUrl),
                requestParam.getOriginUrl(),
                LinkUtil.getLinkCacheValidTime(requestParam.getValidDate()),
                TimeUnit.MILLISECONDS);
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://"+shortLinkDO.getFullShortUrl())
                .originUrl(shortLinkDO.getOriginUrl())
                .gid(requestParam.getGid())
                .build();
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelFlag, 0)
                .orderByDesc(ShortLinkDO::getCreateTime);
        IPage<ShortLinkDO> resultPage = baseMapper.selectPage(requestParam, queryWrapper);
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            result.setDomain("http://"+result.getDomain());
            return result;
        });
    }

    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        QueryWrapper<ShortLinkDO> queryWrapper = Wrappers.query(new ShortLinkDO())
                .select("gid as gid, count(*) as shortLinkCount")
                .in("gid", requestParam)
                .eq("enable_status", 0)
                .groupBy("gid");
        List<Map<String, Object>> shortLinkDOList = baseMapper.selectMaps(queryWrapper);
        return BeanUtil.copyToList(shortLinkDOList, ShortLinkGroupCountQueryRespDTO.class);
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0);
        ShortLinkDO hasShortLinkDO = baseMapper.selectOne(queryWrapper);
        if (hasShortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(hasShortLinkDO.getDomain())
                .shortUri(hasShortLinkDO.getShortUri())
                .favicon(hasShortLinkDO.getFavicon())
                .createdType(hasShortLinkDO.getCreatedType())
                .gid(requestParam.getGid())
                .originUrl(requestParam.getOriginUrl())
                .describe(requestParam.getDescribe())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .build();
        //判断是否要修改该短链接的分组
        //如果不修改的话，就正常更新
        //如果修改，要在原组里面删除，然后在新组里面插入
        if(Objects.equals(hasShortLinkDO.getGid(),requestParam.getGid())){//不修改
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .set(Objects.equals(requestParam.getValidDateType(), VailDateTypeEnum.PERMANENT.getType()), ShortLinkDO::getValidDate, null);
            baseMapper.update(shortLinkDO,updateWrapper);
        }else {
            LambdaUpdateWrapper<ShortLinkDO> deleteWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, hasShortLinkDO.getFullShortUrl())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0);
            baseMapper.delete(deleteWrapper);
            baseMapper.insert(shortLinkDO);
        }
    }

    @SneakyThrows
    @Override
    public void restoreUrl(String shortUri, ServletRequest request, ServletResponse response) {
        String serverName = request.getServerName();
        String fullShortUrl = serverName + "/" + shortUri;

        //解决缓存穿透
        //1. 判断缓存中是否存在
        String originalUrl = stringRedisTemplate.opsForValue().get(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl));
        if(StrUtil.isNotBlank(originalUrl)){
            ((HttpServletResponse)response).sendRedirect(originalUrl);
            return;
        }
        //2. 缓存中不存在的话，查看布隆过滤器中是否存在
        boolean hasContain = shortUriCreateCachePenetrationBloomFilter.contains(fullShortUrl);
        if(!hasContain){//布隆过滤器中不存在，说明数据库中没有，那么就直接返回
            ((HttpServletResponse)response).sendRedirect("/page/notfound");
            return;
        }
        //3. 查询缓存中是否存在空值的key
        String gotoIsNullShortLink = stringRedisTemplate.opsForValue().get(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
        if(StrUtil.isNotBlank(gotoIsNullShortLink)){//存在空值的key，直接返回
            ((HttpServletResponse)response).sendRedirect("/page/notfound");
            return;
        }
        //4. 使用分布式锁请求数据库（缓存穿透）
        //为了解决缓存击穿（某一个key突然失效，然后很多请求都来访问这个key，这样就导致请求都访问到数据库上）
        //使用分布式锁
        RLock lock = redissonClient.getLock(String.format(RedisKeyConstant.LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock();
        try{
            //双重判断key是否存在缓存
            originalUrl = stringRedisTemplate.opsForValue().get(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl));
            if(StrUtil.isNotBlank(originalUrl)){
                ((HttpServletResponse)response).sendRedirect(originalUrl);
                return;
            }


            //先到路由表查询这个fullShortUrl对应的Gid（因为t_link是根据Gid来分片的）
            LambdaQueryWrapper<ShortLinkGotoDO> linkGotoQueryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(linkGotoQueryWrapper);
            if(shortLinkGotoDO==null){
                stringRedisTemplate.opsForValue().set(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY,fullShortUrl),"-",30, TimeUnit.MINUTES);
                ((HttpServletResponse)response).sendRedirect("/page/notfound");
                return;
            }
            //然后到t_link表获取originalUrl
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, shortLinkGotoDO.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0);
            ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);
            //数据过期或者不存在数据库中（已经被删除 DelFlag = 1）
            if(shortLinkDO==null||shortLinkDO.getValidDate().before(new Date())) {
                //存入到缓存，value设置为null
                stringRedisTemplate.opsForValue().set(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl), "-", 30, TimeUnit.MINUTES);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                return;
            }
            //数据库存在查询的数据并且没有过期，存入到缓存
            stringRedisTemplate.opsForValue().set(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY,fullShortUrl),
                    shortLinkDO.getOriginUrl(),
                    LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate()),
                    TimeUnit.MILLISECONDS);
            //转发到原始链接
            ((HttpServletResponse)response).sendRedirect(shortLinkDO.getOriginUrl());

        }finally {
            lock.unlock();
        }




    }

    /**
     * 生成短链接 六位 后缀码
     * @param requestParam
     * @return
     */

    private String generateSuffix(ShortLinkCreateReqDTO requestParam){
        int customGenerateCount = 0;//生成的次数
        String shortLinkSuffix;
        while (true){
            if(customGenerateCount>10){
                throw new ServiceException("短链接频繁生成，请稍后再试");
            }
            String originUrl = requestParam.getOriginUrl();
            //拼接UUID保证后面每次通过hash算法生成的shortUrl是不同的
            originUrl += UUID.randomUUID().toString();
            shortLinkSuffix = HashUtil.hashToBase62(originUrl);
            if(!shortUriCreateCachePenetrationBloomFilter.contains(requestParam.getDomain()+"/"+shortLinkSuffix)){
                break;
            }
            customGenerateCount++;
        }
        return shortLinkSuffix;
    }

    /**
     * 获取网站图标
     * @param url
     * @return
     */
    @SneakyThrows
    private String getFavicon(String url) {
        URL targetUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (HttpURLConnection.HTTP_OK == responseCode) {
            Document document = Jsoup.connect(url).get();
            Element faviconLink = document.select("link[rel~=(?i)^(shortcut )?icon]").first();
            if (faviconLink != null) {
                return faviconLink.attr("abs:href");
            }
        }
        return null;
    }
}
