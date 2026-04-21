package org.qianye.common;

import lombok.Data;

import java.util.List;

/**
 * 分页数据封装类
 */
@Data
public class PageData<T extends List> {
    private T list;
    private long total;
    private long pageNum;
    private long pageSize;

    public PageData(T queueGroupDTOS, long current, long size, long total) {
        this.list = queueGroupDTOS;
        this.total = total;
        this.pageNum = current;
        this.pageSize = size;
    }
}
