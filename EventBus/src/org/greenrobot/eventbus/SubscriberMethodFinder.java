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
package org.greenrobot.eventbus;

import org.greenrobot.eventbus.meta.SubscriberInfo;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订阅者方法查找器
 */
class SubscriberMethodFinder {
    /*
     * In newer class files, compilers may add methods. Those are called bridge or synthetic methods.
     * EventBus must ignore both. There modifiers are not public but defined in the Java class file format:
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
     */
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;

    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
    /**
     * 订阅者方法缓存 ConcurrentHashMap，为了避免重复查找订阅者的订阅方法，维护了此缓存
     * key:   Class<?>                 订阅者 Class 对象
     * value: List<SubscriberMethod>>  订阅者方法 List
     */
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();
    // 订阅者索引类集合
    private List<SubscriberInfoIndex> subscriberInfoIndexes;
    // 是否进行严格的方法验证 默认值为 false
    private final boolean strictMethodVerification;
    // 是否忽略生成的索引 默认值为 false
    private final boolean ignoreGeneratedIndex;

    // FIND_STATE_POOL 长度
    private static final int POOL_SIZE = 4;
    // FindState 池，默认4个位置 POOL_SIZE = 4
    private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];

    SubscriberMethodFinder(List<SubscriberInfoIndex> subscriberInfoIndexes, boolean strictMethodVerification,
                           boolean ignoreGeneratedIndex) {
        this.subscriberInfoIndexes = subscriberInfoIndexes;
        this.strictMethodVerification = strictMethodVerification;
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
    }

    /**
     * 查找订阅者方法
     *
     * @param subscriberClass Class<?> 订阅者 Class 对象
     * @return List<SubscriberMethod> 订阅者方法List
     */
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        // 首先尝试从缓存中获取订阅方法 List
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        // 判断从缓存中获取订阅方法 List 是否为null
        if (subscriberMethods != null) {
            // 如果不为 null，就返回缓存中的订阅方法 List
            return subscriberMethods;
        }

        // 是否忽略生成的索引
        if (ignoreGeneratedIndex) {
            // 忽略索引的情况下，通过反射进行查找订阅者方法
            subscriberMethods = findUsingReflection(subscriberClass);
        } else {
            subscriberMethods = findUsingInfo(subscriberClass);
        }
        if (subscriberMethods.isEmpty()) {
            throw new EventBusException("Subscriber " + subscriberClass
                    + " and its super classes have no public methods with the @Subscribe annotation");
        } else {
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
            return subscriberMethods;
        }
    }

    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {
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

    private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
        List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
        findState.recycle();
        synchronized (FIND_STATE_POOL) {
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
     * 准备 FindState，会从对象池中去获取，没有缓存的情况下会去 new 一个新对象
     * 此处运用了对象复用及池化技术
     *
     * @return FindState
     */
    private FindState prepareFindState() {
        // 加锁，监视器为 FindState 池
        synchronized (FIND_STATE_POOL) {
            // 对 FindState 池进行遍历
            for (int i = 0; i < POOL_SIZE; i++) {
                FindState state = FIND_STATE_POOL[i];
                // 对池中指定索引位置获取的值进行判空，如果不为 null 将此对象返回，并且将该索引位置置空
                if (state != null) {
                    FIND_STATE_POOL[i] = null;
                    return state;
                }
            }
        }
        // 如果缓存池中没有缓存的对象，就去 new 一个
        return new FindState();
    }

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
     * 通过反射查找订阅者方法
     *
     * @param subscriberClass Class<?> 订阅者 Class 对象
     * @return List<SubscriberMethod> 订阅者方法 List
     */
    private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
        // 获取一个 FindState 对象
        FindState findState = prepareFindState();
        // 为当前订阅者初始化 FindState
        findState.initForSubscriber(subscriberClass);
        // 循环 从子类到父类查找订阅方法
        while (findState.clazz != null) {
            // 从当前 findState.clazz 指向的类中使用反射查找订阅方法
            findUsingReflectionInSingleClass(findState);
            // 查找阶段结束，移动到当前类的父类
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    /**
     * 通过反射查找 {@link FindState#clazz} 对应类的订阅者方法
     *
     * @param findState FindState
     */
    private void findUsingReflectionInSingleClass(FindState findState) {
        // 声明一个 方法数组
        Method[] methods;
        try {
            // 通过反射获取当前 Class 的方法，某些 Android 版本在调用 getDeclaredMethods 或 getMethods 时似乎存在反射错误
            // getMethods(): 该方法是获取本类以及父类或者父接口中所有的公共方法(public修饰符修饰的)。
            // getDeclaredMethods(): 该方法是获取本类中的所有方法，包括私有的(private、protected、默认以及public)的方法。
            // getDeclaredMethods() 这比 getMethods() 快，尤其是当订阅者是像活动这样的巨大的类时
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable th) {
            // Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
            try {
                // 当 getDeclaredMethods() 发生异常时，尝试使用 getMethods()
                methods = findState.clazz.getMethods();
            } catch (LinkageError error) { // super class of NoClassDefFoundError to be a bit more broad...
                // 当 getMethods() 也产生了异常时，会抛出 EventBusException 异常
                String msg = "Could not inspect methods of " + findState.clazz.getName();
                if (ignoreGeneratedIndex) {
                    msg += ". Please consider using EventBus annotation processor to avoid reflection.";
                } else {
                    msg += ". Please make this class visible to EventBus annotation processor to avoid reflection.";
                }
                throw new EventBusException(msg, error);
            }
            // 跳过父类的方法查找
            findState.skipSuperClasses = true;
        }

        // 代码执行到此处表示反射获取方法没有产生异常
        // 对获取到的类中的所有方法进行遍历操作
        for (Method method : methods) {
            // 获取方法的修饰符
            int modifiers = method.getModifiers();
            // 订阅者方法有一定的限制，必须为 public，不能是static volatile abstract strict
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                // 获取该方法的参数列表
                Class<?>[] parameterTypes = method.getParameterTypes();

                // 对参数个数进行判断
                if (parameterTypes.length == 1) {
                    // 参数个事为1的情况下，符合订阅者方法对于参数个数的限制
                    // 获取方法的注解，判断是否存在 Subscribe
                    Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                    if (subscribeAnnotation != null) {
                        // 如果方法存在 Subscribe 注解
                        // 获取方法参数的 Class 对象
                        Class<?> eventType = parameterTypes[0];
                        if (findState.checkAdd(method, eventType)) {
                            ThreadMode threadMode = subscribeAnnotation.threadMode();
                            findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                    subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                        }
                    }
                } else
                    // 继续严格的方法验证判断
                    if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                    throw new EventBusException("@Subscribe method " + methodName +
                            "must have exactly 1 parameter but has " + parameterTypes.length);
                }

            } else
                // 方法不是 public，进一步判断
                // 判断是否进行严格的方法验证，如果是，再判断当前方法是否有 Subscribe 注解
                // 两个条件都成立的情况下，抛出 EventBusException 异常，该异常来自于严格的方法验证
                if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(methodName +
                        " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
            }
        }
    }

    static void clearCaches() {
        METHOD_CACHE.clear();
    }

    /**
     * 查找状态
     * 主要就是在查找订阅者方法的过程中记录一些状态信息
     * FindState 类是 SubscriberMethodFinder 的内部类，这个方法主要做一个初始化的工作。
     * <p>
     * 由于该类中字段多，为了内存做了对象缓存池处理，见{@link #FIND_STATE_POOL}
     */
    static class FindState {
        // 订阅者方法 List
        final List<SubscriberMethod> subscriberMethods = new ArrayList<>();
        // 按事件类型区分的方法 HashMap
        final Map<Class, Object> anyMethodByEventType = new HashMap<>();
        final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();
        // StringBuilder
        final StringBuilder methodKeyBuilder = new StringBuilder(128);

        // 订阅者的 Class 对象
        Class<?> subscriberClass;
        // 当前类的 Class 对象，该字段会随着从子类到父类查找的过程而进行赋值当前类的 Class 对象
        Class<?> clazz;
        // 是否跳过父类的方法查找
        boolean skipSuperClasses;
        // 索引类
        SubscriberInfo subscriberInfo;

        /**
         * 为订阅者进行初始化
         *
         * @param subscriberClass Class<?> 订阅者 Class 对象
         */
        void initForSubscriber(Class<?> subscriberClass) {
            this.subscriberClass = clazz = subscriberClass;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

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
         * 检查并将方法添加
         * @param method Method 订阅方法
         * @param eventType Class<?> 事件 Class 对象，也就是该方法的形参
         * @return 校验结果
         */
        boolean checkAdd(Method method, Class<?> eventType) {
            // 2 级检查：第1级仅具有事件类型（快速），第2级在需要具有完整签名
            // 通常订阅者没有监听相同事件类型的方法
            // 将方法先放进 anyMethodByEventType 中
            Object existing = anyMethodByEventType.put(eventType, method);
            // 对 put 返回结果进行判断
            if (existing == null) {
                // null 的情况，表示该 map 中没有相同方法，校验成功
                return true;
            } else {
                // 判断是否是 Method 类型
                if (existing instanceof Method) {
                    // 进行方法校验
                    if (!checkAddWithMethodSignature((Method) existing, eventType)) {
                        // 抛出异常
                        throw new IllegalStateException();
                    }
                    // Put any non-Method object to "consume" the existing Method
                    anyMethodByEventType.put(eventType, this);
                }
                return checkAddWithMethodSignature(method, eventType);
            }
        }

        /**
         * 检查并将方法添加，对方法签名校验
         * @param method Method 订阅方法
         * @param eventType Class<?> 事件 Class 对象，也就是该方法的形参
         * @return 校验结果
         */
        private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {
            methodKeyBuilder.setLength(0);
            methodKeyBuilder.append(method.getName());
            methodKeyBuilder.append('>').append(eventType.getName());
            // 方法键：方法名>Class name
            String methodKey = methodKeyBuilder.toString();
            Class<?> methodClass = method.getDeclaringClass();
            Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);
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
         * 移动到父类
         */
        void moveToSuperclass() {
            if (skipSuperClasses) {
                clazz = null;
            } else {
                clazz = clazz.getSuperclass();
                String clazzName = clazz.getName();
                // Skip system classes, this degrades performance.
                // Also we might avoid some ClassNotFoundException (see FAQ for background).
                if (clazzName.startsWith("java.") || clazzName.startsWith("javax.") ||
                        clazzName.startsWith("android.") || clazzName.startsWith("androidx.")) {
                    clazz = null;
                }
            }
        }
    }

}
