package com.itheima.reggie.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.Dto.SetmealDto;
import com.itheima.reggie.common.R;
import com.itheima.reggie.entity.*;
import com.itheima.reggie.service.CategoryService;
import com.itheima.reggie.service.SetmealDishService;
import com.itheima.reggie.service.SetmealService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequestMapping("/setmeal")
public class SetmealController
{
    @Autowired
    private SetmealService setmealService;

    @Autowired
    private SetmealDishService setmealDishService;

    @Autowired
    private CategoryService categoryService;

    /**
     * 新增套餐
     * @param setmealDto
     * @return
     */
    @PostMapping("")
    @CacheEvict(value = "setmealCache", allEntries = true)
    public R<String> save(@RequestBody SetmealDto setmealDto){
        setmealService.saveWithDish(setmealDto);
        return R.success("添加套餐成功");
    }

    @GetMapping("/page")
    public R<Page> page(int page,int pageSize,String name){
        //构造分页构造器
        Page<Setmeal> pageinfo = new Page<>(page,pageSize);
        Page<SetmealDto> page1 = new Page<>();
        //构造条件构建器
        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper();
        //过滤条件
        queryWrapper.like(Strings.isNotEmpty(name),Setmeal::getName,name);
        //排序条件
        queryWrapper.orderByDesc(Setmeal::getUpdateTime);
        //执行查询
        setmealService.page(pageinfo, queryWrapper);

        BeanUtils.copyProperties(pageinfo,page1);
        List<Setmeal> records = pageinfo.getRecords();
        List<SetmealDto> list =  records.stream().map((item)->{
            SetmealDto setmealDto = new SetmealDto();
            BeanUtils.copyProperties(item,setmealDto);
            Long categoryId = item.getCategoryId();
            Category byId = categoryService.getById(categoryId);
            if(byId != null){
                String categoryName = byId.getName();
                setmealDto.setCategoryName(categoryName);
            }
            return setmealDto;
        }).collect(Collectors.toList());
        page1.setRecords(list);
        return R.success(page1);
    }

    @DeleteMapping
    @CacheEvict(value = "setmealCache", allEntries = true)  //allEntries清理所有相关缓存的数据
    public R<String > delete(@RequestParam List<Long> ids){
        setmealService.removeWithDish(ids);
        return R.success("删除选中套餐成功");
    }


    @GetMapping("/{id}")
    public R<SetmealDto> getById(@PathVariable Long id){
        SetmealDto data = setmealService.getByIdWithDish(id);
        return R.success(data);
    }

    @Transactional
    @PutMapping("")         //bug:前端选中相同菜品时,会额外单列而不是叠加数量
    public R<String> update(@RequestBody SetmealDto setmealDto){
        //更新套餐基本信息
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDto,setmeal);
        setmealService.updateById(setmeal);
        //删除或添加改动菜品信息
        LambdaQueryWrapper<SetmealDish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SetmealDish::getSetmealId,setmeal.getId());
        setmealDishService.remove(queryWrapper);
        List<SetmealDish> dishs = setmealDto.getSetmealDishes();
        dishs.stream().map((item)->{
            item.setSetmealId(setmeal.getId());
            return item;
        }).collect(Collectors.toList());
        setmealDishService.saveBatch(dishs);
        return R.success("修改套餐信息成功");
    }

    @Transactional      //无法禁售
    @PostMapping("/status/0")
    public R<String> status0(@RequestParam List<Long> ids){
        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Setmeal::getId,ids);
        List<Setmeal> setmeals = ids.stream().map((item)->{
            Setmeal setmeal = setmealService.getById(item);
            setmeal.setStatus(0);
            return setmeal;
        }).collect(Collectors.toList());
        setmealService.updateBatchById(setmeals);
        return R.success("停售套餐成功");
    }

    /**
     * 启售
     * @param ids
     * @return
     */
    @Transactional
    @PostMapping("/status/1")
    public R<String> status1(@RequestParam List<Long> ids){
        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Setmeal::getId,ids);
        List<Setmeal> setmeals = ids.stream().map((item)->{
            Setmeal setmeal = setmealService.getById(item);
            setmeal.setStatus(1);
            return setmeal;
        }).collect(Collectors.toList());
        setmealService.updateBatchById(setmeals);
        return R.success("启售套餐成功");
    }

    /**
     * 根据条件查询套餐
     * @param setmeal
     * @return
     */
    @Cacheable(value = "setmealCache",key="#setmeal.categoryId +'_'+#setmeal.status")           //返回的对象需要实现序列化方法
    @GetMapping("/list")
    public R<List<Setmeal>> list(Setmeal setmeal){
        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(setmeal.getCategoryId() != null,Setmeal::getCategoryId,setmeal.getCategoryId());
        queryWrapper.eq(setmeal.getStatus() != null,Setmeal::getStatus,setmeal.getStatus());
        queryWrapper.orderByDesc(Setmeal::getUpdateTime);
        List<Setmeal> list = setmealService.list(queryWrapper);
        return R.success(list);
    }

}
