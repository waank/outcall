package org.qianye.common;

public enum TaskStatusEnum {
    INIT(0),
    RUNNING(1),
    FINISHED(2),
    STOP(3);
    private final int code;

    TaskStatusEnum(int code) {
        this.code = code;
    }

    public static boolean allowCallUpgrade(Integer status) {
        return status != null && status == RUNNING.code;
    }

    public int getCode() {
        return code;
    }
}
