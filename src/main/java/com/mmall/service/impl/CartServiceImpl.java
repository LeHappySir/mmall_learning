package com.mmall.service.impl;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.mmall.common.Const;
import com.mmall.common.ResponseCode;
import com.mmall.common.ServerResponse;
import com.mmall.dao.CartMapper;
import com.mmall.dao.ProductMapper;
import com.mmall.pojo.Cart;
import com.mmall.pojo.Product;
import com.mmall.service.ICartService;
import com.mmall.util.BigDecimalUtil;
import com.mmall.util.PropertiesUtil;
import com.mmall.vo.CartProductVo;
import com.mmall.vo.CartVo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by 81975 on 2017/8/20.
 */
@Service("iCartService")
public class CartServiceImpl implements ICartService {

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 添加商品到购物车,默认已勾选
     * @param userId 用户ID
     * @param productId 商品ID
     * @param count 商品数量
     * @return 返回已对购物车做了处理的数据CartVo对象
     */
    @Override
    public ServerResponse<CartVo> add(Integer userId,Integer productId,Integer count){
        if (productId == null || count == null){
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ILLEGAL_ARGUMENT.getCode(),ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        Cart cart = cartMapper.selectCartByUserIdProductId(userId,productId);
        if (cart==null){//添加商品到购物车
            Cart cartItem = new Cart();
            cartItem.setUserId(userId);
            cartItem.setProductId(productId);
            cartItem.setChecked(Const.Cart.CHECKED);
            cartItem.setQuantity(count);
            cartMapper.insert(cartItem);
        }else {//更新商品在购物车的数量
            count = cart.getQuantity()+count;
            cart.setQuantity(count);
            cartMapper.updateByPrimaryKeySelective(cart);
        }
        return this.list(userId);
    }

    /**
     * 更新购物车中的某条数据,
     * 重新设置购买商品的数量
     * @param userId 用户ID
     * @param productId 商品ID
     * @param count 商品数量
     * @return 返回已对购物车做了处理的数据CartVo对象
     */
    @Override
    public ServerResponse<CartVo> update(Integer userId,Integer productId,Integer count){
        if (productId==null || count == null){
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ILLEGAL_ARGUMENT.getCode(),ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        Cart cart = cartMapper.selectCartByUserIdProductId(userId,productId);
        if (cart!=null){
            cart.setQuantity(count);
        }
        cartMapper.updateByPrimaryKeySelective(cart);
        return this.list(userId);
    }

    /**
     * 删除购物车中的商品
     * @param userId 用户ID
     * @param productIds 商品ID集合
     * @return 返回已对购物车做了处理的数据CartVo对象
     */
    @Override
    public ServerResponse<CartVo> deleteProducts(Integer userId,String productIds){
        List<String> productIdList = Splitter.on(",").splitToList(productIds);
        if (CollectionUtils.isEmpty(productIdList)){
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ILLEGAL_ARGUMENT.getCode(),ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        cartMapper.deleteByUserIdProductIds(userId,productIdList);
        return this.list(userId);
    }

    /**
     * 得到购物车数据集合
     * @param userId
     * @return 返回已对购物车做了处理的数据CartVo对象
     */
    @Override
    public ServerResponse<CartVo> list(Integer userId){
        CartVo cartVo = this.getCartVoLimit(userId);
        return ServerResponse.createBySuccess(cartVo);
    }

    /**
     * 改变勾选状态,全选、全反选、单选、取消单选
     * @param userId
     * @param productId
     * @param checked
     * @return
     */
    @Override
    public ServerResponse<CartVo> selectOrUnSelect(Integer userId,Integer productId,Integer checked){
        cartMapper.checkedOrUnCheckedProduct(userId,productId,checked);
        return this.list(userId);
    }

    /**
     * 得到用户添加商品到购物车的个数
     * @param userId
     * @return
     */
    @Override
    public ServerResponse<Integer> getCartProductCount(Integer userId){
        if (userId==null){
            return ServerResponse.createBySuccess(0);
        }
        int count = cartMapper.selectCartProductCount(userId);
        return ServerResponse.createBySuccess(count);
    }


    /**
     *根据用户搜索出购物车列表的数据,每一条数据对应一个CartProductVo对象
     * 每一个CartProductVo对象都存在有商品信息和此商品购买的数量,若购买的数量超过商品的库存,
     * 则需要更此数据的购买数量,若此商品在购物车中已勾选则需要计算入CartVo对象的总价中,最后得出
     * 此用户在购物车的商品是否全选了
     * @param userId
     * @return
     */
    private CartVo getCartVoLimit(Integer userId){
        CartVo cartVo = new CartVo();
        List<Cart> cartList = cartMapper.selectCartByUserId(userId);
        List<CartProductVo> cartProductVoList = Lists.newArrayList();
        BigDecimal cartTotalPrice = new BigDecimal("0");
        for (Cart cart:cartList) {
            CartProductVo cartProductVo = new CartProductVo();
            cartProductVo.setId(cart.getId());
            cartProductVo.setUserId(cart.getUserId());
            cartProductVo.setProductId(cart.getProductId());

            Product product = productMapper.selectByPrimaryKey(cart.getProductId());
            if (product!=null){
                cartProductVo.setProductMainImage(product.getMainImage());
                cartProductVo.setProductName(product.getName());
                cartProductVo.setProductSubtitle(product.getSubtitle());
                cartProductVo.setProductPrice(product.getPrice());
                cartProductVo.setProductStock(product.getStock());
                cartProductVo.setProductStatus(product.getStatus());

                int buyLimitCount = 0;
                if (product.getStock() >= cart.getQuantity()){//比较商品的实际库存和用户将此商品加入购物车的实际数量
                    cartProductVo.setLimitQuantity(Const.Cart.LIMIT_NUM_SUCCESS);
                    buyLimitCount = cart.getQuantity();
                }else {//若超过商品的实际库存则更新该购物车数据的购物数量让其等于商品库存
                    buyLimitCount = product.getStock();
                    cartProductVo.setLimitQuantity(Const.Cart.LIMIT_NUM_FAIL);
                    //购物车中更新有效库存
                    Cart cartForQuantity = new Cart();
                    cartForQuantity.setId(cart.getId());
                    cartForQuantity.setQuantity(buyLimitCount);
                    cartMapper.updateByPrimaryKeySelective(cartForQuantity);
                }
                cartProductVo.setQuantity(buyLimitCount);
                //计算该商品在购物车上的总价,即商品单价乘以购买数量
                cartProductVo.setProductTotalPrice(BigDecimalUtil.mul(product.getPrice().doubleValue(),cartProductVo.getQuantity().doubleValue()));
                //该商品是否已勾选
                cartProductVo.setProductChecked(cart.getChecked());
            }
            if (cart.getChecked()==Const.Cart.CHECKED){//若商品已勾选则加入购物车总价的计算中
                cartTotalPrice = BigDecimalUtil.add(cartTotalPrice.doubleValue(),cartProductVo.getProductTotalPrice().doubleValue());
            }
            cartProductVoList.add(cartProductVo);
        }
        cartVo.setCartTotalPrice(cartTotalPrice);
        cartVo.setCartProductVoList(cartProductVoList);
        cartVo.setImageHost(PropertiesUtil.getProperty("ftp.server.http.prefix"));
        cartVo.setAllChecked(this.getAllCheckedStatus(userId));
        return cartVo;
    }

    /**
     * 判断是否全勾选
     * @param userId
     * @return
     */
    private boolean getAllCheckedStatus(Integer userId){
        if(userId == null){
            return false;
        }
        //判断是否全勾选,查看购物车中没勾选的商品数量
        return cartMapper.selectCartProductCheckedByUserId(userId) == 0;
    }

}
