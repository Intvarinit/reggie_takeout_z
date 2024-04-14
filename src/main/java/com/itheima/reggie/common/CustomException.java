package com.itheima.reggie.common;

//自定义异常业务类
public class CustomException extends RuntimeException{
    public CustomException(String message){
        super(message);
    }
}
