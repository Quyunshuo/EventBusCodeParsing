package org.greenrobot.eventbus.android;

import android.os.Looper;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.HandlerPoster;
import org.greenrobot.eventbus.MainThreadSupport;
import org.greenrobot.eventbus.Poster;

/**
 * 默认的 Android 主线程支持
 */
public class DefaultAndroidMainThreadSupport implements MainThreadSupport {
    /**
     * 当前是否是主线程
     */
    @Override
    public boolean isMainThread() {
        // 通过当前 Looper 对比主线程 Looper，来判断当前线程是否是主线程
        return Looper.getMainLooper() == Looper.myLooper();
    }

    /**
     * 创建 HandlerPoster 事件发布器
     */
    @Override
    public Poster createPoster(EventBus eventBus) {
        return new HandlerPoster(eventBus, Looper.getMainLooper(), 10);
    }
}
