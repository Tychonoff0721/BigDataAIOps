package com.aiops.bigdata.entity.common;

import lombok.Data;

/**
 * 统一API响应封装类
 */
@Data
public class ApiResponse<T> {
    
    private Integer code;
    private String message;
    private T data;
    private Long timestamp = System.currentTimeMillis();
    
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
    
    public static <T> ApiResponse<T> error(Integer code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
    
    public static <T> ApiResponse<T> badRequest(String message) {
        return error(400, message);
    }
    
    public static <T> ApiResponse<T> internalError(String message) {
        return error(500, message);
    }
}
