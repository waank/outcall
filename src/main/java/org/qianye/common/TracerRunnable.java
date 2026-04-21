package org.qianye.common;

/**
 * 支持链路追踪的Runnable基类
 */
public abstract class TracerRunnable implements Runnable {
    @Override
    public void run() {
        doRun();
    }

    public abstract void doRun();
}
