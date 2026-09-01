package com.eastmoney.agent.base;

import lombok.Data;

/**
 * @Author: suyue
 * @name: RestResponse
 * @Date: 2026/09/01
 * @Description: 统一接口返回对象
 */
@Data
public class RestResponse<T> {

    /** 业务状态码，0 表示成功 */
    private Integer code;

    /** 接口返回信息 */
    private String message;

    /** 接口返回数据 */
    private T data;

    public static <T> RestResponse<T> success(T data) {
        RestResponse<T> response = new RestResponse<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static RestResponse<Void> fail(String message) {
        RestResponse<Void> response = new RestResponse<>();
        response.setCode(1);
        response.setMessage(message);
        return response;
    }

    public static RestResponse<Void> exception() {
        RestResponse<Void> response = new RestResponse<>();
        response.setCode(500);
        response.setMessage("系统异常，请查看服务日志");
        return response;
    }
}
