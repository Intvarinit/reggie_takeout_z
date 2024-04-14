package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itheima.reggie.common.BaseContext;
import com.itheima.reggie.common.R;
import com.itheima.reggie.entity.User;
import com.itheima.reggie.service.UserService;
import com.itheima.reggie.utils.SMSUtils;
import com.itheima.reggie.utils.ValidateCodeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@Slf4j
@RequestMapping("user/")
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 发送手机验证码
     * @param user
     * @return
     */
    @PutMapping("/sendMsg")
    public R<String> sendMsg(@RequestBody User user, HttpSession session){
        //获取手机号
        String phone = user.getPhone();
        if(Strings.isNotEmpty(phone)) {
            //生产随机4位验证码
            String code = ValidateCodeUtils.generateValidateCode(4).toString();
            //调用阿里云的短信api完成短信发送
//            SMSUtils.sendMessage("阿里云短信测试","SMS_154950909",phone,code);
            //保存验证码并进行后续比对
//            session.setAttribute(phone, code);
            //将验证码保存到redis，有效期五分钟
            redisTemplate.opsForValue().set(phone,code,5, TimeUnit.MINUTES);

            System.out.println("验证码: "+code);
            return R.success("验证码发送成功");

        }
        return R.error("验证码发送失败");
    }

    @PostMapping("/login")
    public R<User> login(@RequestBody Map user, HttpSession session){
        //从map获取手机号和验证码
        String phone = (String) user.get("phone");
        String  code = (String)user.get("code");
        //从session获取保存好的验证码
//        String codeInSession = (String) session.getAttribute(phone);
        //从redis获取缓存的验证码
        Object codeInRedis = redisTemplate.opsForValue().get(phone);
        //比对验证码
        if(codeInRedis != null && codeInRedis.equals(code)){
            //登录成功
            LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(User::getPhone,phone);
            User curUser = userService.getOne(queryWrapper);
            //新用户自动注册
            if(curUser == null){
                curUser = new User();
                curUser.setPhone(phone);
                curUser.setStatus(1);
                userService.save(curUser);
            }
            session.setAttribute("user",curUser.getId());       //session赋值以通过登录校验

            //登录成功即删除缓存中的验证码
            redisTemplate.delete(phone);
            return R.success(curUser);
        }
        return R.error("登录失败");
    }

    /**
     * 处理前端用户退出登录的情况
     * @return
     */
    @PostMapping("loginout")
    public R<String> loginout(HttpSession session){
        //删除当前用户的session中所有的值
        session.invalidate();
        return R.success("退出登录成功");
    }
}
