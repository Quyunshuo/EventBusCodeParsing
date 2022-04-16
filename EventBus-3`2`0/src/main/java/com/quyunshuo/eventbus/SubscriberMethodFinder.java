/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.quyunshuo.eventbus;

import com.quyunshuo.eventbus.meta.SubscriberInfo;
import com.quyunshuo.eventbus.meta.SubscriberInfoIndex;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订阅方法查找器
 * 内部维护一个承载订阅者及订阅者的订阅方法的map容器
 */
class SubscriberMethodFinder {
    /*
     * In newer class files, compilers may add methods. Those are called bridge or synthetic methods.
     * EventBus must ignore both. There modifiers are not public but defined in the Java class file format:
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
     */
    private static final int BRIDGE = 0x40; // 64

    private static final int SYNTHETIC = 0x1000; //4096

    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;

    // 订阅者容器  <订阅者,订阅者的订阅方法>
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();

    private List<SubscriberInfoIndex> subscriberInfoIndexes;

    // 是否是严格的方法验证
    private final boolean strictMethodVerification;

    // 是否没使用索引
    private final boolean ignoreGeneratedIndex;

    // FindState池大小
    private static final int POOL_SIZE = 4;

    // FindState池
    private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];

    SubscriberMethodFinder(List<SubscriberInfoIndex> subscriberInfoIndexes, boolean strictMethodVerification,
                           boolean ignoreGeneratedIndex) {
        this.subscriberInfoIndexes = subscriberInfoIndexes;
        this.strictMethodVerification = strictMethodVerification;
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
    }

    /**
     * 查找订阅方法
     *
     * @param subscriberClass 订阅者的Class对象
     * @return 订阅方法
     */
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        // 从订阅者容器中尝试get订阅者的订阅方法
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        // 如果不为空就表示已经注册 然后返回该订阅者的订阅方法
        if (subscriberMethods != null) {
            return subscriberMethods;
        }
        // 为空继续往下走
        // 是否没使用索引 该处是一个重要拐点 当使用者开启了apt后，将先通过索引进行查找，否则使用最原始的反射进行查找
        if (ignoreGeneratedIndex) {
            // 没使用索引  使用反射进行查找订阅者的订阅方法
            subscriberMethods = findUsingReflection(subscriberClass);
        } else {
            // 如果开启了索引 会走findUsingInfo()去查找订阅者的订阅方法
            subscriberMethods = findUsingInfo(subscriberClass);
        }
        // 判断订阅方法是否为null
        if (subscriberMethods.isEmpty()) {
            throw new EventBusException("Subscriber " + subscriberClass
                    + " and its super classes have no public methods with the @Subscribe annotation");
        } else {
            // 向订阅者容器put订阅者以及他的订阅方法
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
            return subscriberMethods;
        }
    }

    /**
     * 通过索引来查找订阅方法
     *
     * @param subscriberClass 订阅者
     * @return 订阅方法
     */
    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        // 拿到FindState实例
        FindState findState = prepareFindState();
        // FindState初始化
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {
            // 获取订阅者的信息  刚初始化的时候返回为null
            findState.subscriberInfo = getSubscriberInfo(findState);
            if (findState.subscriberInfo != null) {
                SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
                for (SubscriberMethod subscriberMethod : array) {
                    if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                        findState.subscriberMethods.add(subscriberMethod);
                    }
                }
            } else {
                findUsingReflectionInSingleClass(findState);
            }
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    /**
     * 获取最终订阅方法的集合
     *
     * @param findState FindState
     * @return 最终订阅方法的集合
     */
    private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
        List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
        // 回收资源
        findState.recycle();
        synchronized (FIND_STATE_POOL) {
            // 对FindState进行回收
            for (int i = 0; i < POOL_SIZE; i++) {
                if (FIND_STATE_POOL[i] == null) {
                    FIND_STATE_POOL[i] = findState;
                    break;
                }
            }
        }
        return subscriberMethods;
    }

    /**
     * 这个方法是创建一个新的FindState类，通过两种方法获取
     * 一种是从FIND_STATE_POOL即FindState池中取出可用的FindState
     * 如果没有的话，则通过第二种方式：直接new一个新的FindState对象
     *
     * @return
     */
    private FindState prepareFindState() {
        synchronized (FIND_STATE_POOL) {
            // 对FindState池进行遍历
            for (int i = 0; i < POOL_SIZE; i++) {
                FindState state = FIND_STATE_POOL[i];
                if (state != null) {
                    FIND_STATE_POOL[i] = null;
                    return state;
                }
            }
        }
        return new FindState();
    }

    /**
     * 获取订阅者的信息
     *
     * @param findState FindState
     * @return 初始化的时候，findState.subscriberInfo和subscriberInfoIndexes为空
     */
    private SubscriberInfo getSubscriberInfo(FindState findState) {
        if (findState.subscriberInfo != null && findState.subscriberInfo.getSuperSubscriberInfo() != null) {
            SubscriberInfo superclassInfo = findState.subscriberInfo.getSuperSubscriberInfo();
            if (findState.clazz == superclassInfo.getSubscriberClass()) {
                return superclassInfo;
            }
        }
        if (subscriberInfoIndexes != null) {
            for (SubscriberInfoIndex index : subscriberInfoIndexes) {
                SubscriberInfo info = index.getSubscriberInfo(findState.clazz);
                if (info != null) {
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * 使用反射查找订阅者的订阅者的订阅方法
     *
     * @param subscriberClass 订阅者的Class对象
     * @return
     */
    private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
        // 拿到FindState实例
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {
            // 查询订阅方法 保存在findState中
            findUsingReflectionInSingleClass(findState);
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    /**
     * 通过反射的方式获取订阅者类中的所有声明方法，然后在这些方法里面寻找以@Subscribe作为注解的方法进行处理（！！！部分的代码）
     * 先经过一轮检查，看看findState.subscriberMethods是否存在，如果没有的话，将方法名，threadMode，优先级，
     * 是否为sticky方法封装为SubscriberMethod对象，添加到subscriberMethods列表中。
     *
     * @param findState
     */
    private void findUsingReflectionInSingleClass(FindState findState) {
        // 订阅者的所有方法
        Method[] methods;
        try {
            // This is faster than getMethods, especially when subscribers are fat classes like Activities
            // getDeclaredMethods()可以拿到反射类中的公共方法、私有方法、保护方法、默认访问，但不获得继承的方法。
            // getMethods()可以拿到反射类及其父类中的所有公共方法。
            // 通过反射去查询到订阅者所有的方法
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable th) {
            // Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
            // 在特定的手机上，崩溃
            try {
                methods = findState.clazz.getMethods();
            } catch (LinkageError error) { // super class of NoClassDefFoundError to be a bit more broad...
                // TODO: 2020/4/30 可以使用StringBuffer优化
                String msg = "Could not inspect methods of " + findState.clazz.getName();
                if (ignoreGeneratedIndex) {
                    msg += ". Please consider using EventBus annotation processor to avoid reflection.";
                } else {
                    msg += ". Please make this class visible to EventBus annotation processor to avoid reflection.";
                }
                throw new EventBusException(msg, error);
            }
            findState.skipSuperClasses = true;
        }
        // 对订阅者所有的方法进行遍历
        for (Method method : methods) {
            // 获取方法的修饰符
            int modifiers = method.getModifiers();
            // 必须是public 不能是static volatile abstract strict
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                // 获取符合条件方法的参数类型列表
                Class<?>[] parameterTypes = method.getParameterTypes();
                // 参数数量必须是1个
                if (parameterTypes.length == 1) {
                    // 获取方法上的注解Subscribe
                    Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                    // ！！！
                    // 判断是否有Subscribe注解
                    if (subscribeAnnotation != null) {
                        // 取第一个方法参数的Class对象
                        Class<?> eventType = parameterTypes[0];
                        // 检查方法 true继续
                        if (findState.checkAdd(method, eventType)) {
                            // 获取注解指定的线程模型
                            ThreadMode threadMode = subscribeAnnotation.threadMode();
                            // 将订阅方法包装为SubscriberMethod添加到subscriberMethods中
                            findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                    subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                        }
                    }

                } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                    throw new EventBusException("@Subscribe method " + methodName +
                            "must have exactly 1 parameter but has " + parameterTypes.length);
                }
            } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(methodName +
                        " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
            }
        }
    }

    /**
     * 清除订阅者容器缓存
     */
    static void clearCaches() {
        METHOD_CACHE.clear();
    }

    /**
     * FindState看名字翻译就是一个寻找状态,主要就是在寻找订阅方法的过程中记录一些状态信息
     * FindState类是SubscriberMethodFinder的内部类，这个方法主要做一个初始化的工作。
     */
    static class FindState {

        // 订阅方法
        final List<SubscriberMethod> subscriberMethods = new ArrayList<>();

        // 不管什么方法  一进来就放在此map
        final Map<Class, Object> anyMethodByEventType = new HashMap<>();

        // 订阅类的方法key
        final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();

        final StringBuilder methodKeyBuilder = new StringBuilder(128);

        // 订阅者
        Class<?> subscriberClass;

        // 订阅者
        Class<?> clazz;

        // 当无法通过反射进行查找 报java.lang.NoClassDefFoundError错时 跳过父类查找
        boolean skipSuperClasses;

        // 订阅者的信息
        SubscriberInfo subscriberInfo;

        /**
         * 初始化
         *
         * @param subscriberClass 订阅者Class对象
         */
        void initForSubscriber(Class<?> subscriberClass) {
            this.subscriberClass = clazz = subscriberClass;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        /**
         * 回收资源
         */
        void recycle() {
            subscriberMethods.clear();
            anyMethodByEventType.clear();
            subscriberClassByMethodKey.clear();
            methodKeyBuilder.setLength(0);
            subscriberClass = null;
            clazz = null;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        /**
         * 检查添加 订阅方法
         * 本质就是检查有无同名同参方法 有就抛出异常
         *
         * @param method    初步符合条件的订阅方法
         * @param eventType 第一个参数类型的Class对象
         * @return
         */
        boolean checkAdd(Method method, Class<?> eventType) {
            // 2 level check: 1st level with event type only (fast), 2nd level with complete signature when required.
            // Usually a subscriber doesn't have methods listening to the same event type.
            // 添加到任何事件类型的方法Map中
            Object existing = anyMethodByEventType.put(eventType, method);
            // 如果existing==null 表示没有相同键 反之是有相同的键 返回该键原始值并将新值对其覆盖
            if (existing == null) {
                return true;
            } else {
                // 判断被替换掉的是否是Method
                if (existing instanceof Method) {
                    // 进行方法签名检查 判断是否没有同名同参方法
                    if (!checkAddWithMethodSignature((Method) existing, eventType)) {
                        // Paranoia check
                        throw new IllegalStateException();
                    }
                    // Put any non-Method object to "consume" the existing Method
                    anyMethodByEventType.put(eventType, this);
                }
                return checkAddWithMethodSignature(method, eventType);
            }
        }

        /**
         * 进行方法签名检查 判断是否没有同名同参方法 肯定返回true
         *
         * @param method    被替换掉的订阅方法
         * @param eventType 新订阅方法的参数类型
         * @return
         */
        private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {
            methodKeyBuilder.setLength(0);
            methodKeyBuilder.append(method.getName());
            methodKeyBuilder.append('>').append(eventType.getName());

            String methodKey = methodKeyBuilder.toString();
            // 返回该方法的类Class对象
            Class<?> methodClass = method.getDeclaringClass();
            // 添加到订阅类的订阅方法map
            Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);
            // 判断是否存在过同名同参方法
            if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
                // Only add if not already found in a sub class
                return true;
            } else {
                // Revert the put, old class is further down the class hierarchy
                subscriberClassByMethodKey.put(methodKey, methodClassOld);
                return false;
            }
        }

        /**
         * 移至父类
         */
        void moveToSuperclass() {
            // 如果skipSuperClasses为true就不对父类型进行遍历了
            if (skipSuperClasses) {
                clazz = null;
            } else {
                // 获取父类Class对象
                clazz = clazz.getSuperclass();
                String clazzName = clazz.getName();
                // Skip system classes, this degrades performance.
                // Also we might avoid some ClassNotFoundException (see FAQ for background).
                // 跳过系统类，这会降低性能。 //同样，我们也可以避免一些ClassNotFoundException
                if (clazzName.startsWith("java.") || clazzName.startsWith("javax.") ||
                        clazzName.startsWith("android.") || clazzName.startsWith("androidx.")) {
                    clazz = null;
                }
            }
        }
    }
}