package com.mmall.service;

import com.mmall.common.ServerResponse;

import java.util.Map;

/**
 * Created by 81975 on 2017/8/27.
 */
public interface IOrderService {

    ServerResponse pay(Integer useId, Long orderNo, String path);

    ServerResponse alipayCallback(Map<String,String> params);

    ServerResponse queryOrderPayStatus(Integer userId,Long orderNo);
}
