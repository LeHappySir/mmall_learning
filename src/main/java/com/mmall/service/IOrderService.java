package com.mmall.service;

import com.github.pagehelper.PageInfo;
import com.mmall.common.ServerResponse;
import com.mmall.vo.OrderVo;

import java.util.Map;

/**
 * Created by 81975 on 2017/8/27.
 */
public interface IOrderService {

    ServerResponse create(Integer userId,Integer shippingId);

    ServerResponse pay(Integer useId, Long orderNo, String path);

    ServerResponse alipayCallback(Map<String,String> params);

    ServerResponse queryOrderPayStatus(Integer userId,Long orderNo);

    ServerResponse<String> cancel(Integer userId,Long orderNo);

    ServerResponse getOrderCartProduct(Integer userId);

    ServerResponse<OrderVo> getOrderDetail(Integer userId, long orderNo);

    ServerResponse<PageInfo> getOrderList(Integer userId, Integer pageNum, Integer pageSize);

    ServerResponse<PageInfo> manageList(int pageNum,int pageSize);

    ServerResponse<OrderVo> manageDetail(long orderNo);

    ServerResponse<PageInfo> manageSearch(long orderNo,int pageNum,int pageSize);

    ServerResponse<String> manageSendGoods(long orderNo);
}
