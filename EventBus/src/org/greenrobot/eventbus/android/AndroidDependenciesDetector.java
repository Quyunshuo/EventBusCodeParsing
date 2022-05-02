package org.greenrobot.eventbus.android;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Android 依赖检测器
 */
@SuppressWarnings("TryWithIdenticalCatches")
public class AndroidDependenciesDetector {
    /**
     * AndroidSDK 是否可用,用于判断平台
     */
    public static boolean isAndroidSDKAvailable() {

        try {
            // 通过反射获取 android.os.Looper Class 对象
            Class<?> looperClass = Class.forName("android.os.Looper");
            Method getMainLooper = looperClass.getDeclaredMethod("getMainLooper");
            Object mainLooper = getMainLooper.invoke(null);
            return mainLooper != null;
        }
        catch (ClassNotFoundException ignored) {}
        catch (NoSuchMethodException ignored) {}
        catch (IllegalAccessException ignored) {}
        catch (InvocationTargetException ignored) {}

        return false;
    }

    private static final String ANDROID_COMPONENTS_IMPLEMENTATION_CLASS_NAME = "org.greenrobot.eventbus.android.AndroidComponentsImpl";

    /**
     * 是否可用 Android 组件，用于判断是否当前是否依赖了 EventBus 的 Android 兼容库
     */
    public static boolean areAndroidComponentsAvailable() {

        try {
            Class.forName(ANDROID_COMPONENTS_IMPLEMENTATION_CLASS_NAME);
            return true;
        }
        catch (ClassNotFoundException ex) {
            return false;
        }
    }

    /**
     * 通过反射实例化Android组件
     */
    public static AndroidComponents instantiateAndroidComponents() {

        try {
            Class<?> impl = Class.forName(ANDROID_COMPONENTS_IMPLEMENTATION_CLASS_NAME);
            return (AndroidComponents) impl.getConstructor().newInstance();
        }
        catch (Throwable ex) {
            return null;
        }
    }
}
