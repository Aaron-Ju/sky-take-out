package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.google.j2objc.annotations.AutoreleasePool;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;


    /**
     * y用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //1.处理各种业务异常（地址簿为空，购物车数据为空）

        //查询订单的地址簿是否为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder().userId(userId).build();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if(shoppingCartList==null || shoppingCartList.size()==0){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //2.向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orders.setAddress(addressBook.getDetail());

        orderMapper.insert(orders);

        //3.向订单明细表插入n条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for(ShoppingCart cart : shoppingCartList){
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        //4.清空当前用户购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        //5.封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception{
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        /*JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }*/

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        //为替代微信支付成功后的数据库订单状态更新，多定义一个方法进行修改
        Integer OrderPaidStatus = Orders.PAID; //支付状态，已支付
        Integer OrderStatus = Orders.TO_BE_CONFIRMED;  //订单状态，待接单

        //发现没有将支付时间 check_out属性赋值，所以在这里更新
        LocalDateTime check_out_time = LocalDateTime.now();

        //获取订单号码
        String orderNumber = ordersPaymentDTO.getOrderNumber();

        log.info("调用updateStatus，用于替换微信支付更新数据库状态的问题");
        orderMapper.updateStatus(OrderStatus, OrderPaidStatus, check_out_time, orderNumber);

        return vo;
    }


    /**
     * 查询历史订单
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        //设置分页
        PageHelper.startPage(pageNum,pageSize);
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        //分页查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        //查询出订单明细，并封装进OrderVO进行相应
        List<OrderVO> list = new ArrayList<>();
        for(Orders orders : page){
            long orderId = orders.getId();

            List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);
            OrderVO orderVO  = new OrderVO();
            BeanUtils.copyProperties(orders,orderVO);
            orderVO.setOrderDetailList(orderDetails);
            list.add(orderVO);
        }

        return new PageResult(list.size(),list);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    public OrderVO detail(Long id) {
        Orders orders = orderMapper.getById(id);
        OrderVO orderVO = new OrderVO();
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);

        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetails);
        return orderVO;
    }
}
