package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.Dto.DishDto;
import com.itheima.reggie.common.R;
import com.itheima.reggie.entity.*;
import com.itheima.reggie.mapper.SetmealDishMapper;
import com.itheima.reggie.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/dish")
public class DishController {
    @Autowired
    private DishService dishService;

    @Autowired
    private DishFlavorService dishFlavorService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 新增菜品
     * @param dto
     * @return
     */
    @PostMapping("")
    public R<String> add(@RequestBody DishDto dto){
        dishService.saveWithFalvor(dto);
        //对单类餐品缓存进行清理
        String key = "dish_" + dto.getCategoryId() + "_1";
        redisTemplate.delete(key);
        return R.success("单品添加成功");
    }

    /**
     * 菜单信息分页
     * @param page
     * @param pageSize
     * @param name
     * @return
     */
    @GetMapping("/page")
    public  R<Page> page(int page,int pageSize,String name) throws IOException {

        //构造分页构造器
        Page<Dish> pageinfo = new Page(page,pageSize);
        Page<DishDto> dishDtoPage = new Page<>();

        //构造条件构建器
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper();
        //过滤条件
        queryWrapper.like(Strings.isNotEmpty(name),Dish::getName,name);
        //排序条件
        queryWrapper.orderByDesc(Dish::getUpdateTime);
        //执行查询
        dishService.page(pageinfo, queryWrapper);
        //对象拷贝
        BeanUtils.copyProperties(pageinfo,dishDtoPage,"records");
        List<Dish> records = pageinfo.getRecords();
        //将categoryName赋值
        List<DishDto> list = records.stream().map((item)->{
            DishDto dishDto =  new DishDto();
            BeanUtils.copyProperties(item,dishDto);
            //查询dish的分类
            Long categoryId = item.getCategoryId();
            Category category = categoryService.getById(categoryId);
            String name1 = category.getName();
            dishDto.setCategoryName(name1);
            return dishDto;
        }).collect(Collectors.toList());

        dishDtoPage.setRecords(list);
        return R.success(dishDtoPage);
    }

    /**
     * 根据id查询菜品和口味信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public R<DishDto> get(@PathVariable Long id){
        DishDto dishDto = dishService.getByIdWithFlavor(id);
        return R.success(dishDto);
    }

    /**
     * 更新菜品信息
     * @param dishDto
     * @return
     */
    @PutMapping("")
    public R<String> update(@RequestBody DishDto dishDto){
        dishService.updateWithFalvors(dishDto);
        //清理菜品缓存数据
//        Set keys = redisTemplate.keys("dish_*");
        //对单类餐品缓存进行清理
        String key = "dish_" + dishDto.getCategoryId() + "_1";
        redisTemplate.delete(key);
        return R.success("更新菜品信息成功");
    }

    /**
     * 根据条件查询菜品数据
     */
    @GetMapping("/list")
    public R<List<DishDto>> list(Dish dish){
        //动态构造redis的key
        List<DishDto> dishDtoList = null;
        String key = "dish_" + dish.getCategoryId() + "_" + dish.getStatus();
        //先从redis获取缓存数据，存在直接返回，不存在则查数据库，然后将数据缓存入redis
        dishDtoList = (List<DishDto>)redisTemplate.opsForValue().get(key);
        if(dishDtoList != null){
            return R.success(dishDtoList);
        }
        //不存在则往下执行
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper();
        //过滤条件
        queryWrapper.eq(dish.getCategoryId() != null,Dish::getCategoryId,dish.getCategoryId());
        queryWrapper.eq(Dish::getStatus,1);     //只找在售状态的菜品
        queryWrapper.orderByAsc(Dish::getSort).orderByDesc(Dish::getUpdateTime);
        List<Dish> list =  dishService.list(queryWrapper);

        dishDtoList = list.stream().map((item)->{
            DishDto dishDto =  new DishDto();
            BeanUtils.copyProperties(item,dishDto);
            //查询dish的分类
            Long categoryId = item.getCategoryId();
            Category category = categoryService.getById(categoryId);
            String name1 = category.getName();
            dishDto.setCategoryName(name1);
            Long dishId = item.getId();
            LambdaQueryWrapper<DishFlavor> queryWrapper1 = new LambdaQueryWrapper();
            queryWrapper1.eq(DishFlavor::getDishId,dishId);
            List<DishFlavor> flavors = dishFlavorService.list(queryWrapper1);
            dishDto.setFlavors(flavors);
            return dishDto;
        }).collect(Collectors.toList());

        //不存在则查数据库，然后将数据缓存入redis,60分钟过期
        redisTemplate.opsForValue().set(key,dishDtoList,60, TimeUnit.MINUTES);

        return R.success(dishDtoList);
    }

    /**
     * 停售
     * @param ids
     * @return
     */
    @Transactional
    @PostMapping("/status/0")
    public R<String> status0(@RequestParam List<Long> ids){
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Dish::getId,ids);
        List<Dish> dishs = ids.stream().map((item)->{
            Dish dish = dishService.getById(item);
            //对单类餐品缓存进行清理
            String key = "dish_" + dish.getCategoryId() + "_1";
            redisTemplate.delete(key);
            dish.setStatus(0);
            return dish;
        }).collect(Collectors.toList());
        dishService.updateBatchById(dishs);
        //查看是否停售对应套餐
        LambdaQueryWrapper<SetmealDish> queryWrapper1 = new LambdaQueryWrapper<>();
        queryWrapper1.in(SetmealDish::getDishId,ids);
        List<SetmealDish> setmealDishes = setmealDishMapper.selectList(queryWrapper1);
        if(setmealDishes.size() == 0){
            return R.success("停售商品成功");
        }
        Set<Long> set = new HashSet<>();
        for(SetmealDish item : setmealDishes) {
            set.add(item.getSetmealId());
        }
        List<Long> setmealIds = new ArrayList<>(set);
        //将对应套餐的状态改成停售
        List<Setmeal> setmeals = setmealService.listByIds(setmealIds).stream().map((item)->{
            item.setStatus(0);
            return item;
        }).collect(Collectors.toList());
        setmealService.updateBatchById(setmeals);
        return R.success("停售商品成功");
    }

    /**
     * 启售
     * @param ids
     * @return
     */
    @Transactional
    @PostMapping("/status/1")
    public R<String> status1(@RequestParam List<Long> ids){
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Dish::getId,ids);
        List<Dish> dishs = ids.stream().map((item)->{
            Dish dish = dishService.getById(item);
            //对单类餐品缓存进行清理
            String key = "dish_" + dish.getCategoryId() + "_1";
            redisTemplate.delete(key);
            dish.setStatus(1);
            return dish;
        }).collect(Collectors.toList());
        dishService.updateBatchById(dishs);
        //尝试启售所有相关联的菜单
        LambdaQueryWrapper<SetmealDish> queryWrapper1 = new LambdaQueryWrapper<>();
        queryWrapper1.in(SetmealDish::getDishId,ids);
        List<SetmealDish> setmealDishes = setmealDishMapper.selectList(queryWrapper1);
        if(setmealDishes.size() == 0){
            return R.success("停售商品成功");
        }
        Set<Long> set = new HashSet<>();
        for(SetmealDish item : setmealDishes) {
            set.add(item.getSetmealId());
        }
        List<Long> setmealIds = new ArrayList<>(set);
        //尝试启售每一个相关联菜单
        Set<Long> updates = new HashSet<>();
        for(Long id : setmealIds){
            set.clear();
            LambdaQueryWrapper<SetmealDish> queryWrapper2 = new LambdaQueryWrapper<>();
            queryWrapper2.eq(SetmealDish::getSetmealId,id);
            setmealDishes = setmealDishMapper.selectList(queryWrapper2);
            for(SetmealDish item : setmealDishes){
                set.add(item.getDishId());
            }
            LambdaQueryWrapper<Dish> queryWrapper3 = new LambdaQueryWrapper<>();
            queryWrapper3.eq(Dish::getStatus,0);
            if(dishService.count(queryWrapper3) == 0){      //启用该套餐
                updates.add(id);
            }
        }
        List<Setmeal> setmeals = setmealService.listByIds(new ArrayList<>(updates)).stream().map((item)->{
            item.setStatus(1);
            return item;
        }).collect(Collectors.toList());
        setmealService.updateBatchById(setmeals);
        return R.success("启售商品成功");
    }
}
