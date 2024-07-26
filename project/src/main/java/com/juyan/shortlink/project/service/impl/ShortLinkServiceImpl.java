package com.juyan.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juyan.shortlink.project.common.constant.RedisKeyConstant;
import com.juyan.shortlink.project.common.constant.ShortLinkConstant;
import com.juyan.shortlink.project.common.convention.exception.ClientException;
import com.juyan.shortlink.project.common.convention.exception.ServiceException;
import com.juyan.shortlink.project.common.convention.result.Result;
import com.juyan.shortlink.project.common.enums.VailDateTypeEnum;
import com.juyan.shortlink.project.config.RBloomFilterConfiguration;
import com.juyan.shortlink.project.dao.entity.*;
import com.juyan.shortlink.project.dao.mapper.*;
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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    /**
     * 基础信息监控表
     */
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    /**
     * 地区访问监控表
     */
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;

    /**
     * 操作系统访问监控表
     */
    private final LinkOsStatsMapper linkOsStatsMapper;

    /**
     * 浏览器访问监控表
     */
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;

    /**
     *短链接高频访问ip监控表
     */
    private final LinkAccessLogsMapper linkAccessLogsMapper;

    /**
     * 设备访问监控表
     */
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;

    /**
     * 网络访问监控表
     */
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;

    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;


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
                .totalPv(0)
                .totalUv(0)
                .totalUip(0)
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
            //统计方法要在重定向前调用，不然cookie加不进去
            shortLinkStats(fullShortUrl,null,request,response);
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
                shortLinkStats(fullShortUrl,null,request,response);
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
            //数据过期或者不存在数据库中（已经失效 EnableStatus = 1）
            if(shortLinkDO==null||(shortLinkDO.getValidDate()!=null&&shortLinkDO.getValidDate().before(new Date()))) {
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

            shortLinkStats(fullShortUrl,shortLinkDO.getGid(),request,response);
            //转发到原始链接
            ((HttpServletResponse)response).sendRedirect(shortLinkDO.getOriginUrl());

        }finally {
            lock.unlock();
        }

    }

    private void shortLinkStats(String fullShortUrl, String gid, ServletRequest request, ServletResponse response){
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();

        //通过用户cookie统计uv
        //如果请求里面cookie为空或者key为“uv”的cookie为空，那么说明是用户第一次访问，uv+1并且生成一个cookie添加到response里面
        //uv对应的cookie存在，那么说明同一用户访问，那么uv不变
        try{
            AtomicReference<String> uv = new AtomicReference();
            //新增“uv”对应的cookie任务
            Runnable addResponseCookieTask = ()->{
                uv.set(UUID.fastUUID().toString());
                Cookie uvCookie = new Cookie("uv",uv.get());
                uvCookie.setMaxAge(60*60*24*30);
                uvCookie.setPath(StrUtil.sub(fullShortUrl,fullShortUrl.indexOf("/"),fullShortUrl.length()));
                ((HttpServletResponse)response).addCookie(uvCookie);//设置cookie的作用域，保证只有访问特定短链接时，才会带上这个cookie
                uvFirstFlag.set(Boolean.TRUE);
                stringRedisTemplate.opsForSet().add("short-link:stats:uv:" + fullShortUrl, uv.get());
            };
            if(ArrayUtil.isNotEmpty(cookies)){
                Arrays.stream(cookies)
                        .filter(each -> Objects.equals(each.getName(),"uv"))
                        .findFirst()
                        .map(Cookie::getValue)
                        .ifPresentOrElse(
                                //存在“uv”对应的cookie
                                each ->{
                                    uv.set(each);
                                    //个人觉得Redis判断可以省略
                                    //（这里用Redis的set进行了双重判定，但是我觉得没必要，只需要判断是否携带uv的cookie就可以判断是否为新老用户）
                                    //通过Redis中的set集合判断是否同一用户访问
                                    //如果添加重复key-value，会添加不成功，然后uvFirstFlag置为false
                                    //如果是新的key-value，会添加成功，然后uvFirstFlag置为true
                                    Long add = stringRedisTemplate.opsForSet().add("short-link:stats:uv:" + fullShortUrl, each);
                                    uvFirstFlag.set(add!=null&&add>0L);
                                },
                                //不存在“uv”对应的cookie，那么就新增一个cookie
                                addResponseCookieTask);
            }else {
                addResponseCookieTask.run();
            }
            //统计ip访问次数
            String actualIp = LinkUtil.getActualIp((HttpServletRequest) request);
            Long uipAdd = stringRedisTemplate.opsForSet().add("short-link:stats:uip:" + fullShortUrl, actualIp);
            AtomicBoolean uipFirstFlag = new AtomicBoolean();
            uipFirstFlag.set(uipAdd!=null && uipAdd>0L);


            if(StrUtil.isBlank(gid)){
                LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                        .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
                ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
                gid = shortLinkGotoDO.getGid();
            }
            int hour = DateUtil.hour(new Date(),true);
            Week week = DateUtil.dayOfWeekEnum(new Date());
            int weekValue = week.getIso8601Value();
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .pv(1)
                    .uv(uvFirstFlag.get()?1:0)
                    .uip(uipFirstFlag.get()?1:0)
                    .hour(hour)
                    .weekday(weekValue)
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .date(new Date())
                    .build();
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);

            //统计地区访问监控
            Map<String,Object> localeParamMap = new HashMap<>();
            localeParamMap.put("key",statsLocaleAmapKey);
            localeParamMap.put("ip",actualIp);
            String localeResult = HttpUtil.get(ShortLinkConstant.AMAP_REMOTE_URL, localeParamMap);
            JSONObject localeResultObj = JSON.parseObject(localeResult);
            String infoCode = localeResultObj.getString("infocode");
            LinkLocaleStatsDO linkLocaleStatsDO;
            String actualProvince;
            String actualCity;
            if(StrUtil.isNotBlank(infoCode)&&StrUtil.equals(infoCode,"10000")){
                String province = localeResultObj.getString("province");
                boolean unknownFlag = StrUtil.equals(province,"[]");
                linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .date(new Date())
                        .country("中国")
                        .province(actualProvince = unknownFlag?"未知":province)
                        .city(actualCity = unknownFlag?"未知":localeResultObj.getString("city"))
                        .adcode(unknownFlag?"未知":localeResultObj.getString("adcode"))
                        .cnt(1)
                        .build();
                linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);

                //统计操作系统访问监控
                LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .os(LinkUtil.getOs(((HttpServletRequest) request)))
                        .date(new Date())
                        .cnt(1)
                        .build();
                linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);

                //统计浏览器访问监控
                LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .browser(LinkUtil.getBrowser(((HttpServletRequest) request)))
                        .date(new Date())
                        .cnt(1)
                        .build();
                linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);

                //统计设备访问
                LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .date(new Date())
                        .device(LinkUtil.getDevice((HttpServletRequest) request))
                        .cnt(1)
                        .build();
                linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);

                //统计网络访问
                LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .date(new Date())
                        .network(LinkUtil.getNetwork((HttpServletRequest) request))
                        .cnt(1)
                        .build();
                linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);

                //统计短链接高频ip访问
                LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                        .fullShortUrl(fullShortUrl)
                        .gid(gid)
                        .user(uv.get() )
                        .browser(LinkUtil.getBrowser(((HttpServletRequest) request)))
                        .ip(actualIp)
                        .os(LinkUtil.getOs(((HttpServletRequest) request)))
                        .network(LinkUtil.getNetwork((HttpServletRequest) request))
                        .device(LinkUtil.getDevice((HttpServletRequest) request))
                        .locale(StrUtil.join("-","中国",actualProvince,actualCity))
                        .build();
                linkAccessLogsMapper.insert(linkAccessLogsDO);

                //短链接访问统计自增
                baseMapper.incrementStats(gid,fullShortUrl,1,uvFirstFlag.get()?1:0,uipFirstFlag.get()?1:0);


            }

        }catch (Throwable ex){
            log.error("短链接访问量统计异常",ex);
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
