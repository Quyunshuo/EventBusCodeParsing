package org.greenrobot.eventbus.android;

/**
 * Android 组件的子类
 * {@link AndroidDependenciesDetector} 通过 Java 库中的反射使用。
 */
public class AndroidComponentsImpl extends AndroidComponents {

    public AndroidComponentsImpl() {
        // 调用父类构造
        super(new AndroidLogger("EventBus"), new DefaultAndroidMainThreadSupport());
    }
}
