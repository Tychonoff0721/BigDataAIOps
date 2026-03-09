package com.aiops.bigdata.entity.common;

import lombok.Data;

import java.util.List;

/**
 * 分页响应封装类
 */
@Data
public class PageResponse<T> {
    
    private List<T> items;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Integer totalPages;
    private Boolean hasNext;
    
    public static <T> PageResponse<T> of(List<T> items, Long total, Integer page, Integer pageSize) {
        PageResponse<T> response = new PageResponse<>();
        response.setItems(items);
        response.setTotal(total);
        response.setPage(page);
        response.setPageSize(pageSize);
        
        int totalPages = (int) Math.ceil((double) total / pageSize);
        response.setTotalPages(totalPages);
        response.setHasNext(page < totalPages);
        
        return response;
    }
}
