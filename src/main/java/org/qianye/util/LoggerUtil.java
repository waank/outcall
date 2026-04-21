package org.qianye.util;

import org.slf4j.Logger;

/**
 * 统一日志工具类，封装SLF4J Logger，支持 {} 占位符格式化
 */
public class LoggerUtil {
    public static void info(Logger log, String format, Object... params) {
        if (log.isInfoEnabled()) {
            log.info(format, params);
        }
    }

    public static void error(Logger log, String msg, Exception e) {
        if (log.isErrorEnabled()) {
            log.error(msg, e);
        }
    }

    public static void error(Logger log, Exception e, String msg) {
        if (log.isErrorEnabled()) {
            log.error(msg, e);
        }
    }

    public static void error(Logger log, Exception e, String format, Object... params) {
        if (log.isErrorEnabled()) {
            // 将异常追加到参数末尾，SLF4J会自动识别最后一个Throwable参数作为异常堆栈输出
            Object[] newParams = new Object[params.length + 1];
            System.arraycopy(params, 0, newParams, 0, params.length);
            newParams[params.length] = e;
            log.error(format, newParams);
        }
    }

    public static void error(Logger log, String format, Object... params) {
        if (log.isErrorEnabled()) {
            log.error(format, params);
        }
    }

    public static void warn(Logger log, String msg) {
        if (log.isWarnEnabled()) {
            log.warn(msg);
        }
    }

    public static void warn(Logger log, String format, Object... params) {
        if (log.isWarnEnabled()) {
            log.warn(format, params);
        }
    }
}
