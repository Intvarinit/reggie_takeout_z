package com.itheima.reggie.filter;

import com.alibaba.fastjson.JSON;
import com.itheima.reggie.common.BaseContext;
import com.itheima.reggie.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

//检查用户是否完成登录
@Slf4j
@WebFilter(filterName = "loginCheckFilter",urlPatterns = "/*")
public class LoginCheckFilter implements Filter {

    //路径匹配器,使用通配符
    public static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

//    路径匹配
    public boolean check(String[] urls,String requestUri){
        for(String url : urls){
            boolean match = PATH_MATCHER.match(url, requestUri);
            if(match){
                return true;
            }
        }
        return false;
    }


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest)servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse)servletResponse;

        //url获取
        String requestURI = httpServletRequest.getRequestURI();
        String[] urls = new String[]{       //需要放行的资源
                "/employee/login",
                "/employee/logout",
                "/backend/**",
                "/front/**",
                "/user/sendMsg",           //用于验证码登录
                "/user/login",
                "/doc.html",
                "/webjars/**",
                "/swagger-resources",
                "/v2/api-docs"

        };

        log.info("拦截到请求:{}",requestURI);

        //判断请求是否需要处理
        boolean check = check(urls,requestURI);

//        不需要处理的直接放行
        if(check){
            log.info("本次请求不需要处理");
            filterChain.doFilter(httpServletRequest,httpServletResponse);
            return;
        }

        //判断登录状态,登录了的才放行
        if(httpServletRequest.getSession().getAttribute("employee") != null){
            log.info("用户已经登录");
            Long empId = (Long) httpServletRequest.getSession().getAttribute("employee");
            BaseContext.setCurrentId(empId);
            filterChain.doFilter(httpServletRequest,httpServletResponse);
            return;
        }
        //判断登录状态,登录了的才放行，对于移动端用户
        if(httpServletRequest.getSession().getAttribute("user") != null){
            log.info("用户已经登录");
            Long userId = (Long) httpServletRequest.getSession().getAttribute("user");
            BaseContext.setCurrentId(userId);
            filterChain.doFilter(httpServletRequest,httpServletResponse);
            return;
        }

        //返回未登录的状态让前端拦截器跳转登录页面
        log.info("用户未登录");
        httpServletResponse.getWriter().write(JSON.toJSONString(R.error("NOTLOGIN")));
    }
}
