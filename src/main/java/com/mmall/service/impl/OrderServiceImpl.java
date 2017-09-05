package com.mmall.service.impl;

import com.alipay.api.AlipayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.demo.trade.config.Configs;
import com.alipay.demo.trade.model.ExtendParams;
import com.alipay.demo.trade.model.GoodsDetail;
import com.alipay.demo.trade.model.builder.AlipayTradePrecreateRequestBuilder;
import com.alipay.demo.trade.model.result.AlipayF2FPrecreateResult;
import com.alipay.demo.trade.service.AlipayTradeService;
import com.alipay.demo.trade.service.impl.AlipayTradeServiceImpl;
import com.alipay.demo.trade.utils.ZxingUtils;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mmall.common.Const;
import com.mmall.common.ServerResponse;
import com.mmall.dao.*;
import com.mmall.pojo.*;
import com.mmall.service.IOrderService;
import com.mmall.util.BigDecimalUtil;
import com.mmall.util.DateTimeUtil;
import com.mmall.util.FTPUtil;
import com.mmall.util.PropertiesUtil;
import com.mmall.vo.OrderItemVo;
import com.mmall.vo.OrderProductVo;
import com.mmall.vo.OrderVo;
import com.mmall.vo.ShippingVo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * Created by 81975 on 2017/8/27.
 */
@Service("iOrderService")
public class OrderServiceImpl implements IOrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private PayInfoMapper payInfoMapper;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ShippingMapper shippingMapper;


    /**
     * 前台得到订单列表
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 页容量
     * @return 分页信息
     */
    @Override
    public ServerResponse<PageInfo> getOrderList(Integer userId,Integer pageNum,Integer pageSize){
        PageHelper.startPage(pageNum,pageSize);
        List<Order> orderList = orderMapper.selectByUserId(userId);
        List<OrderVo> orderVoList = this.assembleOrderVoList(orderList,userId);
        PageInfo pageResult = new PageInfo(orderList);
        pageResult.setList(orderVoList);
        return ServerResponse.createBySuccess(pageResult);
    }

    /**
     * 组装OrderVo集合
     * 每一条订单包含订单内容集合,即购买商品的信息OrderItem对象
     * OrderVo对象又此订单和此订单内容即OrderItem组成
     * @param orderList
     * @param userId
     * @return
     */
    private List<OrderVo> assembleOrderVoList(List<Order> orderList,Integer userId){
        List<OrderVo> orderVoList = Lists.newArrayList();
        for (Order order : orderList){
            List<OrderItem> orderItemList = Lists.newArrayList();
            if (userId==null){
                orderItemList = orderItemMapper.selectByOrderNo(order.getOrderNo());
            }else{
                orderItemList = orderItemMapper.selectByUserIdOrderNo(userId,order.getOrderNo());
            }
            OrderVo orderVo = this.assembleOrderVo(order,orderItemList);
            orderVoList.add(orderVo);
        }
        return orderVoList;
    }

    /**
     * 得到订单的详情
     * @param userId 用户ID
     * @param orderNo 订单号
     * @return
     */
    public ServerResponse<OrderVo> getOrderDetail(Integer userId,long orderNo){
        Order order = orderMapper.selectByUserIdOrderNo(userId,orderNo);
        if (order==null){
            return ServerResponse.createByErrorMessage("该用户订单不存在");
        }
        List<OrderItem> orderItemList = orderItemMapper.selectByUserIdOrderNo(userId,orderNo);
        OrderVo orderVo = this.assembleOrderVo(order,orderItemList);
        return ServerResponse.createBySuccess(orderVo);
    }

    /**
     *
     * @param userId
     * @return
     */
    @Override
    public ServerResponse getOrderCartProduct(Integer userId){
        OrderProductVo orderProductVo = new OrderProductVo();
        //从购物车中拿到已勾选的数据
        List<Cart> cartList = cartMapper.selectCheckedCartByUserId(userId);
        ServerResponse serverResponse = this.getCartOrderItem(userId,cartList);
        if (!serverResponse.isSuccess()){
            return serverResponse;
        }
        //得到OrderItem，与购物车数据是一对一关系
        List<OrderItem> orderItemList = (List<OrderItem>) serverResponse.getData();

        //根据OrderItem生成返回前台的OrderItemVo信息
        List<OrderItemVo> orderItemVoList = Lists.newArrayList();
        //此购物车中的总价
        BigDecimal payment = new BigDecimal("0");
        for (OrderItem orderItem : orderItemList){
            payment = BigDecimalUtil.add(payment.doubleValue(),orderItem.getTotalPrice().doubleValue());
            orderItemVoList.add(this.assembleOrderItemVo(orderItem));
        }

        orderProductVo.setImageHost(PropertiesUtil.getProperty(""));
        orderProductVo.setOrderItemVoList(orderItemVoList);
        orderProductVo.setProductTotalPrice(payment);
        return ServerResponse.createBySuccess(orderProductVo);
    }

    /**
     * 取消该订单
     * @param userId
     * @param orderNo
     * @return
     */
    @Override
    public ServerResponse<String> cancel(Integer userId,Long orderNo){
        Order order = orderMapper.selectByUserIdOrderNo(userId,orderNo);
        if (order==null){
            return ServerResponse.createByErrorMessage("该用户订单不存在");
        }
        if (order.getStatus()!=Const.OrderStatusEnum.NO_PAY.getCode()){
            return ServerResponse.createByErrorMessage("已付款,无法取消订单");
        }
        Order updateOrder = new Order();
        updateOrder.setId(order.getId());
        updateOrder.setStatus(Const.OrderStatusEnum.CANCELED.getCode());
        int resultCount = orderMapper.updateByPrimaryKeySelective(updateOrder);
        if (resultCount==0){
            return ServerResponse.createByErrorMessage("取消该订单失败");
        }
        return ServerResponse.createBySuccessMessage("取消订单成功");
    }



    /**
     * 创建订单
     * @param userId 用户ID
     * @param shippingId 地址
     * @return
     */
    @Override
    public ServerResponse create(Integer userId,Integer shippingId){

        //生成订单，先通过用户id查找到购物车已勾选的数据
        List<Cart> cartList = cartMapper.selectCheckedCartByUserId(userId);

        //根据这些数据得到orderItem列表
        ServerResponse serverResponse = this.getCartOrderItem(userId,cartList);
        if (!serverResponse.isSuccess()){
            return serverResponse;
        }
        List<OrderItem> orderItemList = (List<OrderItem>) serverResponse.getData();
        //最后成为订单
        //得到订单的总价
        BigDecimal payment = this.getOrderTotalPrice(orderItemList);
        //生成订单并得到订单
        Order order = this.assembleOrder(userId,shippingId,payment);

        for (OrderItem orderItem: orderItemList) {
            orderItem.setOrderNo(order.getOrderNo());
        }
        //批量插入orderItem
        orderItemMapper.batchInsert(orderItemList);
        //更新库存
        this.reduceProductStock(orderItemList);
        //购物车清空
        this.cleanCart(cartList);

        //返回到前端的内容
        OrderVo orderVo = this.assembleOrderVo(order,orderItemList);
        return ServerResponse.createBySuccess(orderVo);
    }

    private Order assembleOrder(Integer userId,Integer shippingId,BigDecimal payment){
        Order order = new Order();
        long orderNo = this.getOrderNO();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPayment(payment);
        order.setStatus(Const.OrderStatusEnum.NO_PAY.getCode());
        order.setPaymentType(Const.PaymentTypeEnum.ONLINE_PAY.getCode());
        order.setPostage(0);
        order.setShippingId(shippingId);
        int resultCount = orderMapper.insert(order);
        if (resultCount==0){
            return null;
        }
        return order;
    }


    private OrderVo assembleOrderVo(Order order,List<OrderItem> orderItems){
        OrderVo orderVo = new OrderVo();
        orderVo.setOrderNo(order.getOrderNo());
        orderVo.setPaymentType(order.getPaymentType());
        orderVo.setPayment(order.getPayment());
        orderVo.setPaymentTypeDesc(Const.PaymentTypeEnum.codeOf(order.getPaymentType()).getValue());

        orderVo.setShippingId(order.getShippingId());
        Shipping shipping = shippingMapper.selectByPrimaryKey(order.getShippingId());
        if (shipping!=null){
            orderVo.setReceiveName(shipping.getReceiverName());
            orderVo.setShippingVo(assembleShippingVo(shipping));
        }
        orderVo.setStatus(order.getStatus());
        orderVo.setStatusDesc(Const.OrderStatusEnum.codeOf(order.getStatus()).getValue());

        orderVo.setCloseTime(DateTimeUtil.dateToStr(order.getCloseTime()));
        orderVo.setEndTime(DateTimeUtil.dateToStr(order.getEndTime()));
        orderVo.setCreateTime(DateTimeUtil.dateToStr(order.getCreateTime()));
        orderVo.setPaymentTime(DateTimeUtil.dateToStr(order.getPaymentTime()));
        orderVo.setSendTime(DateTimeUtil.dateToStr(order.getSendTime()));

        orderVo.setImageHost(PropertiesUtil.getProperty("ftp.server.http.prefix"));
        List<OrderItemVo> orderItemVos = Lists.newArrayList();
        for (OrderItem orderItem : orderItems){
            orderItemVos.add(this.assembleOrderItemVo(orderItem));
        }
        orderVo.setOrderItemVoList(orderItemVos);
        orderVo.setPostage(order.getPostage());
        return orderVo;
    }


    private OrderItemVo assembleOrderItemVo(OrderItem orderItem){
        OrderItemVo orderItemVo = new OrderItemVo();
        orderItemVo.setQuantity(orderItem.getQuantity());
        orderItemVo.setCreateTime(DateTimeUtil.dateToStr(orderItem.getCreateTime()));
        orderItemVo.setCurrentUnitPrice(orderItem.getCurrentUnitPrice());
        orderItemVo.setOrderNo(orderItem.getOrderNo());
        orderItemVo.setProductId(orderItem.getProductId());
        orderItemVo.setProductImage(orderItem.getProductImage());
        orderItemVo.setProductName(orderItem.getProductName());
        orderItemVo.setTotalPrice(orderItem.getTotalPrice());
        return orderItemVo;
    }

    private ShippingVo assembleShippingVo(Shipping shipping){
        ShippingVo shippingVo = new ShippingVo();
        shippingVo.setReceiverAddress(shipping.getReceiverAddress());
        shippingVo.setReceiverCity(shipping.getReceiverCity());
        shippingVo.setReceiverDistrict(shipping.getReceiverDistrict());
        shippingVo.setReceiverMobile(shipping.getReceiverMobile());
        shippingVo.setReceiverName(shipping.getReceiverName());
        shippingVo.setReceiverPhone(shipping.getReceiverPhone());
        shippingVo.setReceiverProvince(shipping.getReceiverProvince());
        shippingVo.setReceiverZip(shipping.getReceiverZip());
        return shippingVo;
    }


    /**
     * 清空购物车
     * @param cartList
     */
    private void cleanCart(List<Cart> cartList){
        for (Cart cart:cartList){
            cartMapper.deleteByPrimaryKey(cart.getId());
        }
    }

    /**
     * 减少商品库存
     * @param orderItems
     */
    private void reduceProductStock(List<OrderItem> orderItems){
        for (OrderItem orderItem:orderItems){
            Product product = productMapper.selectByPrimaryKey(orderItem.getProductId());
            product.setStock(product.getStock()-orderItem.getQuantity());
            productMapper.updateByPrimaryKeySelective(product);
        }
    }


    /**
     * 得到订单号 并发问题
     * @return
     */
    private long getOrderNO(){
        long currentTime = System.currentTimeMillis();
        return currentTime+new Random().nextInt(100);
    }

    /**
     * 得到这笔订单的总价
     * @param orderItems
     * @return
     */
    private BigDecimal getOrderTotalPrice(List<OrderItem> orderItems){
        BigDecimal orderTotalPrice = new BigDecimal("0");
        for (OrderItem orderItem:orderItems){
            orderTotalPrice = BigDecimalUtil.add(orderTotalPrice.doubleValue(),orderItem.getTotalPrice().doubleValue());
        }
        return orderTotalPrice;
    }


    /**
     * 根据购物车得到OrderItem
     * @param userId
     * @param cartList
     * @return
     */
    private ServerResponse getCartOrderItem(Integer userId,List<Cart> cartList){
        if (CollectionUtils.isEmpty(cartList)){
            return ServerResponse.createByErrorMessage("购物车为空");
        }
        List<OrderItem> orderItemList = Lists.newArrayList();
        for (Cart cartItem: cartList) {
            OrderItem orderItem = new OrderItem();
            Product product = productMapper.selectByPrimaryKey(cartItem.getProductId());
            if (product.getStatus() != Const.ProductStatusEnum.ON_SALE.getCode()){
                return ServerResponse.createByErrorMessage("产品"+product.getName()+"不是在线销售状态");
            }
            if (product.getStock() < cartItem.getQuantity()){
                return ServerResponse.createByErrorMessage("产品"+product.getName()+"库存不足");
            }
            orderItem.setUserId(userId);
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setProductImage(product.getMainImage());
            orderItem.setCurrentUnitPrice(product.getPrice());
            orderItem.setTotalPrice(BigDecimalUtil.mul(cartItem.getQuantity(),product.getPrice().doubleValue()));
            orderItemList.add(orderItem);
        }
        return ServerResponse.createBySuccess(orderItemList);
    }


    /**
     * 订单支付,接通支付宝
     * @param userId 用户ID
     * @param orderNo 订单号
     * @param path 文件路径,存储二维码时需要用到
     * @return
     */
    @Override
    public ServerResponse pay(Integer userId,Long orderNo,String path){

        //返回订单号和二维码的文件地址给前端
        Map<String,String> map = Maps.newHashMap();

        Order order = orderMapper.selectByUserIdOrderNo(userId,orderNo);

        if (order==null){
            return ServerResponse.createByErrorMessage("用户订单不存在");
        }
        map.put("orderNo",order.getOrderNo().toString());

        // (必填) 商户网站订单系统中唯一订单号，64个字符以内，只能包含字母、数字、下划线，
        // 需保证商户系统端不能重复，建议通过数据库sequence生成，
        String outTradeNo = order.getOrderNo().toString();

        // (必填) 订单标题，粗略描述用户的支付目的。如“xxx品牌xxx门店当面付扫码消费”
        String subject = new StringBuilder().append("happymall扫码支付,订单号:").append(outTradeNo).toString();

        // (必填) 订单总金额，单位为元，不能超过1亿元
        // 如果同时传入了【打折金额】,【不可打折金额】,【订单总金额】三者,则必须满足如下条件:【订单总金额】=【打折金额】+【不可打折金额】
        String totalAmount = order.getPayment().toString();

        // (可选) 订单不可打折金额，可以配合商家平台配置折扣活动，如果酒水不参与打折，则将对应金额填写至此字段
        // 如果该值未传入,但传入了【订单总金额】,【打折金额】,则该值默认为【订单总金额】-【打折金额】
        String undiscountableAmount = "0";

        // 卖家支付宝账号ID，用于支持一个签约账号下支持打款到不同的收款账号，(打款到sellerId对应的支付宝账号)
        // 如果该字段为空，则默认为与支付宝签约的商户的PID，也就是appid对应的PID
        String sellerId = "";

        // 订单描述，可以对交易或商品进行一个详细地描述，比如填写"购买商品2件共15.00元"
        String body = new StringBuilder().append("订单").append(outTradeNo).append("购买商品共").append(totalAmount).append("元").toString();

        // 商户操作员编号，添加此参数可以为商户操作员做销售统计
        String operatorId = "test_operator_id";

        // (必填) 商户门店编号，通过门店号和商家后台可以配置精准到门店的折扣信息，详询支付宝技术支持
        String storeId = "test_store_id";

        // 业务扩展参数，目前可添加由支付宝分配的系统商编号(通过setSysServiceProviderId方法)，详情请咨询支付宝技术支持
        ExtendParams extendParams = new ExtendParams();
        extendParams.setSysServiceProviderId("2088100200300400500");

        // 支付超时，定义为120分钟
        String timeoutExpress = "120m";

        // 商品明细列表，需填写购买商品详细信息，
        List<GoodsDetail> goodsDetailList = new ArrayList<GoodsDetail>();

        List<OrderItem> orderItems = orderItemMapper.selectByUserIdOrderNo(userId,orderNo);

        for (OrderItem orderItem: orderItems) {
            // 创建一个商品信息，参数含义分别为商品id（使用国标）、名称、单价（单位为分）、数量，如果需要添加商品类别，详见GoodsDetail
            GoodsDetail good = GoodsDetail.newInstance(orderItem.getProductId().toString(),orderItem.getProductName(),
                    BigDecimalUtil.mul(orderItem.getCurrentUnitPrice().doubleValue(),new Double(100).doubleValue()).longValue(),
                    orderItem.getQuantity());
            goodsDetailList.add(good);
        }

        // 创建扫码支付请求builder，设置请求参数
        AlipayTradePrecreateRequestBuilder builder = new AlipayTradePrecreateRequestBuilder()
                .setSubject(subject).setTotalAmount(totalAmount).setOutTradeNo(outTradeNo)
                .setUndiscountableAmount(undiscountableAmount).setSellerId(sellerId).setBody(body)
                .setOperatorId(operatorId).setStoreId(storeId).setExtendParams(extendParams)
                .setTimeoutExpress(timeoutExpress)
                .setNotifyUrl(PropertiesUtil.getProperty("alipay.callback.url"))//支付宝服务器主动通知商户服务器里指定的页面http路径,根据需要设置
                .setGoodsDetailList(goodsDetailList);

        /** 一定要在创建AlipayTradeService之前调用Configs.init()设置默认参数
         *  Configs会读取classpath下的zfbinfo.properties文件配置信息，如果找不到该文件则确认该文件是否在classpath目录
         */
        Configs.init("zfbinfo.properties");

        /** 使用Configs提供的默认参数
         *  AlipayTradeService可以使用单例或者为静态成员对象，不需要反复new
         */
        AlipayTradeService tradeService = new AlipayTradeServiceImpl.ClientBuilder().build();

        //生成订单
        AlipayF2FPrecreateResult result = tradeService.tradePrecreate(builder);

        //判断订单生成的状态
        switch (result.getTradeStatus()) {
            case SUCCESS:
                log.info("支付宝预下单成功: ");
                AlipayTradePrecreateResponse response = result.getResponse();
                dumpResponse(response);
                //创建文件夹
                File folder = new File(path);
                if (!folder.exists()){
                    folder.setWritable(true);
                    folder.mkdirs();
                }
                // 需要修改为运行机器上的路径
                String qrPath = String.format(path+"/qr-%s.png",response.getOutTradeNo());
                String qrFileName = String.format("qr-%s.png",response.getOutTradeNo());
                //存放二维码到指定路径下
                ZxingUtils.getQRCodeImge(response.getQrCode(), 256, qrPath);

                //上传二维码图片到FTP服务器中
                File targetFile = new File(path,qrFileName);
                try {
                    FTPUtil.uploadFile(Lists.newArrayList(targetFile));
                } catch (IOException e) {
                    log.error("上传二维码异常",e);
                }
                log.info("filePath:" + qrPath);
                String qrUrl = PropertiesUtil.getProperty("ftp.server.http.prefix")+targetFile.getName();
                map.put("qrUrl",qrUrl);
                return ServerResponse.createBySuccess(map);
            case FAILED:
                log.error("支付宝预下单失败!!!");
                return ServerResponse.createByErrorMessage("支付宝预下单失败!!!");
            case UNKNOWN:
                log.error("系统异常，预下单状态未知!!!");
                return ServerResponse.createByErrorMessage("系统异常，预下单状态未知!!!");
            default:
                log.error("不支持的交易状态，交易返回异常!!!");
                return ServerResponse.createByErrorMessage("不支持的交易状态，交易返回异常!!!");
        }

    }

    // 简单打印应答
    private void dumpResponse(AlipayResponse response) {
        if (response != null) {
            log.info(String.format("code:%s, msg:%s", response.getCode(), response.getMsg()));
            if (StringUtils.isNotEmpty(response.getSubCode())) {
                log.info(String.format("subCode:%s, subMsg:%s", response.getSubCode(),
                        response.getSubMsg()));
            }
            log.info("body:" + response.getBody());
        }
    }


    /**
     * 支付宝回调函数
     * 验证支付宝支付成功时的回调状态,若支付成功修改订单的支付状态和添加支付信息
     * @param params
     * @return
     */
    @Override
    public ServerResponse alipayCallback(Map<String,String> params){
        Long orderNo = Long.parseLong(params.get("out_trade_no"));
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order==null){
            return ServerResponse.createByErrorMessage("");
        }
        if (order.getStatus()>= Const.OrderStatusEnum.PAID.getCode()){
            return ServerResponse.createBySuccess("支付宝重复调用");
        }

        if (Const.AlipayCallback.TRADE_STATUS_TRADE_SUCCESS.equals(tradeStatus)){
            order.setPaymentTime(DateTimeUtil.strToDate(params.get("gmt_payment")));
            order.setStatus(Const.OrderStatusEnum.PAID.getCode());
            orderMapper.updateByPrimaryKeySelective(order);
        }

        PayInfo payInfo = new PayInfo();
        payInfo.setUserId(order.getUserId());
        payInfo.setPayPlatform(Const.PayPlatformEnum.ALIPAY.getCode());
        payInfo.setPlatformNumber(tradeNo);
        payInfo.setOrderNo(order.getOrderNo());
        payInfo.setPlatformStatus(tradeStatus);
        payInfoMapper.insert(payInfo);

        return ServerResponse.createBySuccess();

    }


    /**
     * 订单是否已支付
     * 若已支付则返回成功
     * @param userId
     * @param orderNo
     * @return
     */
    @Override
    public ServerResponse queryOrderPayStatus(Integer userId,Long orderNo){

        Order order = orderMapper.selectByUserIdOrderNo(userId,orderNo);

        if (order==null){
            return ServerResponse.createByErrorMessage("用户订单不存在");
        }
        if (order.getStatus()>= Const.OrderStatusEnum.PAID.getCode()){
            return ServerResponse.createBySuccess();
        }
        return ServerResponse.createByError();
    }










//    backend
    @Override
    public ServerResponse<PageInfo> manageList(int pageNum,int pageSize){
        PageHelper.startPage(pageNum,pageSize);
        List<Order> orderList = orderMapper.selectAll();
        List<OrderVo> orderVoList = this.assembleOrderVoList(orderList,null);
        PageInfo pageResult = new PageInfo(orderList);
        pageResult.setList(orderVoList);
        return ServerResponse.createBySuccess(pageResult);
    }

    @Override
    public ServerResponse<OrderVo> manageDetail(long orderNo){
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order==null){
            ServerResponse.createByErrorMessage("此订单不存在");
        }
        List<OrderItem> orderItemList = orderItemMapper.selectByOrderNo(orderNo);
        OrderVo orderVo = this.assembleOrderVo(order,orderItemList);
        return ServerResponse.createBySuccess(orderVo);

    }

    @Override
    public ServerResponse<PageInfo> manageSearch(long orderNo,int pageNum,int pageSize){
        PageHelper.startPage(pageNum,pageSize);
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order==null){
            ServerResponse.createByErrorMessage("此订单不存在");
        }

        List<OrderItem> orderItemList = orderItemMapper.selectByOrderNo(orderNo);
        OrderVo orderVo = this.assembleOrderVo(order,orderItemList);
        PageInfo pageResult = new PageInfo(Lists.newArrayList(order));
        pageResult.setList(Lists.newArrayList(orderVo));
        return ServerResponse.createBySuccess(pageResult);

    }

    @Override
    public ServerResponse<String> manageSendGoods(long orderNo){
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order==null){
            ServerResponse.createByErrorMessage("此订单不存在");
        }
        if (order.getStatus()==Const.OrderStatusEnum.PAID.getCode()){
            order.setSendTime(new Date());
            order.setStatus(Const.OrderStatusEnum.SHIPPED.getCode());
            int rs = orderMapper.updateByPrimaryKeySelective(order);
            if (rs>0){
                return ServerResponse.createBySuccess("发货成功");
            }
        }
        return ServerResponse.createByErrorMessage("订单未付款,发货失败");
    }





}
