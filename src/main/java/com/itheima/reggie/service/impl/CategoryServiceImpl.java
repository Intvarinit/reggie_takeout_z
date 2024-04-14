package com.itheima.reggie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.reggie.common.CustomException;
import com.itheima.reggie.entity.Category;
import com.itheima.reggie.entity.Dish;
import com.itheima.reggie.mapper.CategoryMapper;
import com.itheima.reggie.service.CategoryService;
import com.itheima.reggie.service.DishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Autowired
    private DishService dishService;

    @Autowired
    private CategoryService categoryService;
    /**
     * 根据id删除分类
     * @param id
     */
    @Override
    public void remove(Long id) {
        LambdaQueryWrapper<Dish> dishQueryWrapper= new LambdaQueryWrapper<>();
        dishQueryWrapper.eq(Dish::getCategoryId,id);
        int count1 = dishService.count(dishQueryWrapper);
        //查询当前分类是否关联菜品,有关联则抛出业务异常
        if(count1 > 0){
            throw new CustomException("当前分类项依然有关联菜品,无法删除");
        }
        LambdaQueryWrapper<Dish> setmealQueryWrapper= new LambdaQueryWrapper<>();
        setmealQueryWrapper.eq(Dish::getCategoryId,id);
        int count2 = dishService.count(setmealQueryWrapper);
        //查询当前分类是否关联菜品,有关联则抛出业务异常
        if(count2 > 0){
            throw new CustomException("当前分类项依然有关联套餐,无法删除");
        }
        //正常删除
        categoryService.removeById(id);
    }
}
