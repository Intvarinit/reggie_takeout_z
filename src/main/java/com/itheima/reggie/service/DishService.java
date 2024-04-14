package com.itheima.reggie.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.reggie.Dto.DishDto;
import com.itheima.reggie.entity.Dish;
import com.itheima.reggie.entity.DishFlavor;

public interface DishService extends IService<Dish> {

    //新增菜品,同时增加菜品对应口味
    public void saveWithFalvor(DishDto dto);

    //根据菜品信息查询口味信息
    public DishDto getByIdWithFlavor(Long id);

    void updateWithFalvors(DishDto dishDto);
}
