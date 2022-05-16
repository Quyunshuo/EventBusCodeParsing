package org.greenrobot.eventbus.android;

import org.greenrobot.eventbus.Logger;
import org.greenrobot.eventbus.MainThreadSupport;

/**
 * 安卓组件
 */
public abstract class AndroidComponents {
    /**
     * Android 组件实例
     */
    private static final AndroidComponents implementation;

    static {
        // 实例化 Android 组件
        implementation = AndroidDependenciesDetector.isAndroidSDKAvailable()
                ? AndroidDependenciesDetector.instantiateAndroidComponents()
                : null;
    }

    /**
     * 安卓组件是否可用
     */
    public static boolean areAvailable() {
        return implementation != null;
    }

    /**
     * 获取安卓组件的实例 实际为 {@link org.greenrobot.eventbus.android.AndroidComponentsImpl}
     */
    public static AndroidComponents get() {
        return implementation;
    }

    /**
     * 日志处理器
     */
    public final Logger logger;

    /**
     * 默认的主线程支持
     */
    public final MainThreadSupport defaultMainThreadSupport;

    /**
     * AndroidDependenciesDetector.instantiateAndroidComponents() 会调用 AndroidComponentsImpl 子类构造从而间接调用此方法进行实例化
     *
     * @param logger                   日志处理器
     * @param defaultMainThreadSupport 默认的主线程支持
     */
    public AndroidComponents(Logger logger, MainThreadSupport defaultMainThreadSupport) {
        this.logger = logger;
        this.defaultMainThreadSupport = defaultMainThreadSupport;
    }
}
