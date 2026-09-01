package com.eastmoney.agent.handler;

import com.eastmoney.agent.base.RestResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @Author: suyue
 * @name: ControllerExceptionHandler
 * @Date: 2026/09/01
 * @Description: 接口参数校验异常处理
 */
@RestControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public RestResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldError() == null
                ? "请求参数不正确"
                : exception.getBindingResult().getFieldError().getDefaultMessage();
        return RestResponse.fail(message);
    }
}

