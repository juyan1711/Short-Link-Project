package com.juyan.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juyan.shortlink.admin.common.convention.exception.ClientException;
import com.juyan.shortlink.admin.common.convention.exception.ServiceException;
import com.juyan.shortlink.admin.common.convention.result.Result;
import com.juyan.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.juyan.shortlink.admin.config.RBloomFilterConfiguration;
import com.juyan.shortlink.admin.dao.entity.UserDO;
import com.juyan.shortlink.admin.dao.mapper.UserMapper;
import com.juyan.shortlink.admin.dto.req.UserLoginReqDTO;
import com.juyan.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.juyan.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.juyan.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.juyan.shortlink.admin.dto.resp.UserRespDTO;
import com.juyan.shortlink.admin.service.GroupService;
import com.juyan.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.User;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.juyan.shortlink.admin.common.constant.RedisCacheConstant;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


/**
 * 用户接口实现层
 */
@Service
@RequiredArgsConstructor //构造器方式注入bean
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO>implements UserService {
    /**
     * 布隆过滤器
     */
    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    /**
     * 分布式锁
     */
    private final RedissonClient redissonClient;
    /**
     * Redis
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Group服务
     */
    private final GroupService groupService;

    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if(userDO==null){
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }

    @Override
    public Boolean hasUsername(String username) {
        return !userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    public void register(UserRegisterReqDTO requestParam) {
        if (!hasUsername(requestParam.getUsername())) {
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        }
        //通过分布式锁完善用户注册功能，防止恶意请求：毫秒级触发大量请求去一个未注册的用户名
        RLock lock = redissonClient.getLock(RedisCacheConstant.LOCK_USER_REGISTER_KEY+requestParam.getUsername());
        if(!lock.tryLock()){
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        }
        try{
            int insert = baseMapper.insert(BeanUtil.toBean(requestParam,UserDO.class));
            if(insert<1){
                throw new ClientException(UserErrorCodeEnum.USER_SAVE_ERROR);
            }
            userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
            groupService.saveGroup(requestParam.getUsername(),"默认分组");
        }catch (DuplicateKeyException ex){
            throw new ClientException(UserErrorCodeEnum.USER_EXIST);
        }finally {
            lock.unlock();
        }




    }

    @Override
    public void update(UserUpdateReqDTO requestParam) {
        //TODO: 验证当前要修改的用户是否为当前登录的用户
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        int update = baseMapper.update(BeanUtil.toBean(requestParam,UserDO.class),updateWrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getPassword,requestParam.getPassword())
                .eq(UserDO::getDelFlag,0);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if(userDO==null){
            throw new ClientException("用户不存在");
        }

        Map<Object, Object> hasLoginMap = stringRedisTemplate.opsForHash().entries(RedisCacheConstant.USER_LOGIN_KEY + requestParam.getUsername());
        if (CollUtil.isNotEmpty(hasLoginMap)) {
            String token = hasLoginMap.keySet().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElseThrow(() -> new ClientException("用户登录错误"));
            return new UserLoginRespDTO(token);
        }
        /**
         * Hash
         * Key：login_用户名
         * Value：
         *  Key：token标识
         *  Val：JSON 字符串（用户信息）
         */

        String uuid = UUID.randomUUID().toString();
        stringRedisTemplate.opsForHash().put(RedisCacheConstant.USER_LOGIN_KEY + requestParam.getUsername(), uuid, JSON.toJSONString(userDO));
        stringRedisTemplate.expire(RedisCacheConstant.USER_LOGIN_KEY + requestParam.getUsername(), 30L, TimeUnit.MINUTES);
        return new UserLoginRespDTO(uuid);
    }

    @Override
    public boolean checkLogin(String username,String token) {
        return stringRedisTemplate.opsForHash().get(RedisCacheConstant.USER_LOGIN_KEY+username,token)!=null;
    }

    @Override
    public void logout(String username, String token) {
        if (checkLogin(username, token)) {
            stringRedisTemplate.delete(RedisCacheConstant.USER_LOGIN_KEY + username);
            return;
        }
        throw new ClientException("用户Token不存在或用户未登录");
    }
}
