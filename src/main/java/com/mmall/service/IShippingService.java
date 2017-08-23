package com.mmall.service;

import com.github.pagehelper.PageInfo;
import com.mmall.common.ServerResponse;
import com.mmall.pojo.Shipping;

import java.util.Map;

/**
 * Created by 81975 on 2017/8/23.
 */
public interface IShippingService {

    ServerResponse<Map> add(Integer userId, Shipping shipping);

    ServerResponse delete(Integer shippingId,Integer userId);

    ServerResponse update(Integer userId,Shipping shipping);

    ServerResponse select(Integer userId,Integer shippingId);

    ServerResponse<PageInfo> list(Integer pageNum, Integer pageSize, Integer userId);
}
