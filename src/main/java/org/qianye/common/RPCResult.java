package org.qianye.common;

import lombok.Data;

@Data
public class RPCResult<T> {
    private int code = 200;
    private T data;
    private String message;
}
