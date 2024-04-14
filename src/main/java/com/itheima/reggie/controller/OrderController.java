package com.itheima.reggie.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.Dto.OrdersDto;
import com.itheima.reggie.Dto.SetmealDto;
import com.itheima.reggie.common.BaseContext;
import com.itheima.reggie.common.R;
import com.itheima.reggie.entity.Category;
import com.itheima.reggie.entity.OrderDetail;
import com.itheima.reggie.entity.Orders;
import com.itheima.reggie.entity.Setmeal;
import com.itheima.reggie.service.OrderDetailService;
import com.itheima.reggie.service.OrderService;
import groovyjarjarantlr.collections.impl.LList;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单
 */
@Slf4j
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderDetailService orderDetailService;

    /**
     * 用户下单
     * @param orders
     * @return
     */
    @PostMapping("/submit")
    public R<String> submit(@RequestBody Orders orders){
        log.info("订单数据：{}",orders);
        orderService.submit(orders);
        return R.success("下单成功");
    }

    @GetMapping("userPage")
    public R<Page> userPage(int page, int pageSize){
        //构造分页构造器
        Page<Orders> pageinfo = new Page<>(page,pageSize);
        Page<OrdersDto> ret = new Page<>();
        //构造条件构建器
        LambdaQueryWrapper<Orders> queryWrapper = new LambdaQueryWrapper();
        //条件查询
        Long userId = BaseContext.getCurrentId();
        if(userId == null){
            return R.error("无法获取用户信息");
        }
        queryWrapper.eq(Orders::getUserId,userId);
        //排序条件
        queryWrapper.orderByDesc(Orders::getOrderTime);
        //执行查询
        orderService.page(pageinfo, queryWrapper);
        List<Orders> records = pageinfo.getRecords();
        BeanUtils.copyProperties(ret,pageinfo);
        List<OrdersDto> dtos = records.stream().map((item)->{
            OrdersDto ordersDto = new OrdersDto();
            BeanUtils.copyProperties(item,ordersDto);
            ordersDto.setUserName(item.getUserName());
            ordersDto.setPhone(item.getPhone());
            ordersDto.setAddress(item.getAddress());
            ordersDto.setConsignee(item.getConsignee());
            LambdaQueryWrapper<OrderDetail> queryWrapper1 = new LambdaQueryWrapper<>();
            queryWrapper1.eq(OrderDetail::getOrderId,item.getId());
            //找到订单对应的菜品信息
            List<OrderDetail> list = orderDetailService.list(queryWrapper1);
            ordersDto.setOrderDetails(list);
            return ordersDto;
        }).collect(Collectors.toList());
        ret.setRecords(dtos);
        return R.success(ret);
    }

    @GetMapping("/page")
    public R<Page> page(int page, int pageSize,@RequestParam(required = false) String number,@RequestParam(required = false)String beginTime,@RequestParam(required = false)String endTime){
        //构造分页构造器
        Page<Orders> pageinfo = new Page<>(page,pageSize);
        //构造条件构建器
        LambdaQueryWrapper<Orders> queryWrapper = new LambdaQueryWrapper();
        //条件查询
        queryWrapper.like(Strings.isNotEmpty(number),Orders::getId,number);
        queryWrapper.ge(null != beginTime,Orders::getOrderTime,beginTime)       //时间范围查询
                .le(null != endTime,Orders::getOrderTime,endTime);
        //排序条件
        queryWrapper.orderByDesc(Orders::getOrderTime);
        //执行查询
        orderService.page(pageinfo, queryWrapper);

        return R.success(pageinfo);

    }

    @PutMapping("")
    public R<String> deliver(@RequestBody JSONObject params){
        int status = Integer.parseInt(params.get("status").toString());
        Long id = Long.parseLong(params.get("id").toString());
        Orders order = orderService.getById(id);
        order.setStatus(status);
        orderService.updateById(order);
        return R.success("当前订单开始派送");
    }
}