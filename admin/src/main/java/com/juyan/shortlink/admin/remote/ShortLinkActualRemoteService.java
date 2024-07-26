/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.juyan.shortlink.admin.remote;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.juyan.shortlink.admin.common.convention.result.Result;
import com.juyan.shortlink.admin.remote.dto.req.*;
import com.juyan.shortlink.admin.remote.dto.resp.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短链接中台远程调用服务
 */

public interface ShortLinkActualRemoteService {


    /**
     * 新增短链接
     * @param requestParam
     * @return
     */
    default Result<ShortLinkCreateRespDTO> createShortLink(ShortLinkCreateReqDTO requestParam){
        String resultCreate = HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/create", JSON.toJSONString(requestParam));
        return JSON.parseObject(resultCreate, new TypeReference<>() {
        });
    }

    /**
     * 分页查询
     * @param requestParam 分页查询参数
     * @return 分页查询结果
     */
    default Result<IPage<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam){
        Map<String,Object> requestMap = new HashMap();
        requestMap.put("gid",requestParam.getGid());
        requestMap.put("current",requestParam.getCurrent());
        requestMap.put("size",requestParam.getSize());
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/page", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }


    /**
     * 查询分组下短链接的数量
     * @param requestParam
     * @return
     */
    default Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupShortLinkCount(List<String> requestParam){
        Map<String,Object> requestMap = new HashMap();
        requestMap.put("requestParam",requestParam);
        String resultCount = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/count",requestMap);
        return JSON.parseObject(resultCount, new TypeReference<>() {
        });
    }

    /**
     * 修改短链接
     * @param requestParam
     */

    default void updateShortLink(ShortLinkUpdateReqDTO requestParam){
        String postResult = HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/update", JSON.toJSONString(requestParam));
    }


    /**
     * 根据 URL 获取对应网站的标题
     * @param url
     * @return
     */
    default Result<String> getTitleByUrl(String url){
        String resultResp = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/title?url=" + url);
        return JSON.parseObject(resultResp, new TypeReference<>() {
        });
    }

    /**
     * 保存回收站
     * @param requestParam
     * @return
     */

    default void saveRecycleBin(RecycleBinSaveReqDTO requestParam){
        String postResult = HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/recycle-bin/save", JSON.toJSONString(requestParam));
    }

    /**
     * 分页查询回收站短链接
     * @param requestParam
     * @return
     */
    default Result<IPage<ShortLinkPageRespDTO>> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        Map<String,Object> requestMap = new HashMap();
        requestMap.put("gidList",requestParam.getGidList());
        requestMap.put("current",requestParam.getCurrent());
        requestMap.put("size",requestParam.getSize());
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/recycle-bin/page", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }

    /**
     * 恢复短链接
     * @param requestParam
     */
    default void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam){
        String postResult = HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/recycle-bin/recover", JSON.toJSONString(requestParam));
    }

    /**
     * 回收站彻底消除短链接
     * @param requestParam
     */
    default void removeRecycleBin(RecycleBinRemoveReqDTO requestParam){
        String postResult = HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/recycle-bin/remove", JSON.toJSONString(requestParam));
    }

    /**
     * 访问单个短链接指定时间内监控数据
     */
    default Result<ShortLinkStatsRespDTO> oneShortLinkStats(ShortLinkStatsReqDTO requestParam){
        Map<String,Object> requestMap = new HashMap();
        requestMap.put("fullShortUrl",requestParam.getFullShortUrl());
        requestMap.put("gid",requestParam.getGid());
        requestMap.put("startDate",requestParam.getStartDate());
        requestMap.put("endDate",requestParam.getEndDate());
        String resultResp = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/stats",BeanUtil.beanToMap(requestParam));
        return JSON.parseObject(resultResp, new TypeReference<>() {
        });
    }

    /**
     * 访问单个短链接指定时间内访问记录数据
     */
    default Result<IPage<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam){
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(requestParam, false, true);
        stringObjectMap.remove("orders");
        stringObjectMap.remove("records");
        String resultResp = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/stats/access-record",stringObjectMap);
        return JSON.parseObject(resultResp, new TypeReference<>() {
        });
    }
}
