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
            // 通过索引的方式进行查找
            subscriberMethods = findUsingInfo(subscriberClass);
        }
        // 如果没有订阅者方法，就抛出 EventBusException 异常
        if (subscriberMethods.isEmpty()) {
            throw new EventBusException("Subscriber " + subscriberClass
                    + " and its super classes have no public methods with the @Subscribe annotation");
        } else {
            // 将此订阅者和其订阅者方法添加进缓存中
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
            // 返回查找的订阅者方法
            return subscriberMethods;
        }
    }

    /**
     * 查找订阅者方法
     *
     * @param subscriberClass Class<?> 订阅者 Class 对象
     * @return List<SubscriberMethod> 订阅者方法 List
     */
    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        // 获取一个 FindState 对象
        FindState findState = prepareFindState();
        // 为当前订阅者初始化 FindState
        findState.initForSubscriber(subscriberClass);
        // 循环 从子类到父类查找订阅方法
        while (findState.clazz != null) {
            // 获取索引类并赋值给 findState.subscriberInfo
            findState.subscriberInfo = getSubscriberInfo(findState);
            // 当索引类的信息类不为 null 时，进一步操作
            if (findState.subscriberInfo != null) {
                // 获取索引类的所有订阅者方法
                SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
                // 对订阅者方法数组遍历
                for (SubscriberMethod subscriberMethod : array) {
                    // 检查并将方法添加
                    if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                        // 校验通过，将该订阅方法添加至 subscriberMethods，
                        findState.subscriberMethods.add(subscriberMethod);
                    }
                }
            } else {
                // 如果没有索引类，就使用反射的方式继续查找
                findUsingReflectionInSingleClass(findState);
            }
            // 将 findState 的 Class 对象移至父类型
            findState.moveToSuperclass();
        }
        // 返回查找到的订阅者方法并释放资源
        return getMethodsAndRelease(findState);
    }

    /**
     * 从 FindState 中获取订阅者方法并正式发布
     * 该方法其实只是将形参 findState 中的 subscriberMethods 以新的 List 返回出来
     * 并且还对 findState 做了资源释放及回收的处理
     *
     * @param findState FindState
     * @return List<SubscriberMethod> 订阅者方法
     */
    private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
        // 创建订阅者方法 List，传入 findState.subscriberMethods
        List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
        // 释放 findState 资源，为下一次复用该类做准备
        findState.recycle();
        // 加锁，监视器为 FindState 对象池，将该 FindState 放入池中
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i++) {
                if (FIND_STATE_POOL[i] == null) {
                    FIND_STATE_POOL[i] = findState;
                    break;
                }
            }
        }
        // 返回订阅者方法
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

    /**
     * 获取索引类
     *
     * @param findState FindState 查找状态类
     * @return SubscriberInfo 索引类
     */
    private SubscriberInfo getSubscriberInfo(FindState findState) {
        // TODO: 2022/5/4 findState.subscriberInfo.getSuperSubscriberInfo() 执行两次，可以缩短为一次
        // findState.subscriberInfo 初始状态是为 null 的，唯一赋值途径也是本方法的返回值
        // 当 findState.subscriberInfo 不为 null 时，就表示已经进行了一轮查找了，一轮查找后，理论上已经转向查找其父类了
        // 所以此 if 的操作，其实就是转向父类型
        // 如果在 FindState 中存在索引类，并且索引类的父类不为null，进行下一步操作
        if (findState.subscriberInfo != null && findState.subscriberInfo.getSuperSubscriberInfo() != null) {
            // 获取当前索引类的父类
            SubscriberInfo superclassInfo = findState.subscriberInfo.getSuperSubscriberInfo();
            // 如果 findState 对应的 clazz 是当前获取到的父类，就返回此父类的 SubscriberInfo
            if (findState.clazz == superclassInfo.getSubscriberClass()) {
                return superclassInfo;
            }
        }

        // 判断订阅者索引类集合是否为 null，不为 null 时进行遍历获取
        if (subscriberInfoIndexes != null) {
            for (SubscriberInfoIndex index : subscriberInfoIndexes) {
                // 获取当前 findState.clazz 的订阅者信息类
                SubscriberInfo info = index.getSubscriberInfo(findState.clazz);
                if (info != null) {
                    // 不为 null 时表示已找到该 findState.clazz 对应的索引类的订阅者信息类
                    return info;
                }
            }
        }

        // 找不到索引类，返回null
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
        // 返回查找到的订阅者方法并释放资源
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
            // 跳过父类的方法查找，因为在此 catch 中已经获取了所有父类方法，所以要跳过父类
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
                        // 对订阅者方法进行二级校验
                        if (findState.checkAdd(method, eventType)) {
                            // 获取订阅者方法的线程模型
                            ThreadMode threadMode = subscribeAnnotation.threadMode();
                            // 将此订阅者方法 添加进 subscriberMethods
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

    /**
     * 用于测试
     */
    static void clearCaches() {
        METHOD_CACHE.clear();
    }

    /**
     * 查找状态
     * 主要就是在查找订阅者方法的过程中记录一些状态信息
     * FindState 类是 SubscriberMethodFinder 的内部类
     * <p>
     * 由于该类中字段多，为了内存做了对象缓存池处理，见{@link #FIND_STATE_POOL}
     */
    static class FindState {
        // 订阅者方法 List
        final List<SubscriberMethod> subscriberMethods = new ArrayList<>();
        // 按事件类型区分的方法 HashMap
        final Map<Class, Object> anyMethodByEventType = new HashMap<>();
        // 按方法签名存储
        final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();
        // StringBuilder
        final StringBuilder methodKeyBuilder = new StringBuilder(128);

        // 订阅者的 Class 对象
        Class<?> subscriberClass;
        // 当前类的 Class 对象，该字段会随着从子类到父类查找的过程而进行赋值当前类的 Class 对象
        Class<?> clazz;
        // 是否跳过父类的方法查找
        boolean skipSuperClasses;
        // 索引类 初始值为 null
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

        /**
         * 释放资源，准备下一次复用
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
         * 检查并将方法添加
         *
         * @param method    Method 订阅方法
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
                    // 对相同事件接收方法进行方法签名校验
                    if (!checkAddWithMethodSignature((Method) existing, eventType)) {
                        // 抛出异常
                        throw new IllegalStateException();
                    }
                    // 放置任何非 Method 对象以“使用”现有 Method
                    anyMethodByEventType.put(eventType, this);
                }
                return checkAddWithMethodSignature(method, eventType);
            }
        }

        /**
         * 检查并将方法添加，对方法签名校验
         *
         * @param method    Method 订阅方法
         * @param eventType Class<?> 事件 Class 对象，也就是该方法的形参
         * @return 校验结果
         */
        private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {
            methodKeyBuilder.setLength(0);
            methodKeyBuilder.append(method.getName());
            methodKeyBuilder.append('>').append(eventType.getName());
            // 方法键：方法名>Class name ，例如 onEventTest1>java.lang.String
            String methodKey = methodKeyBuilder.toString();
            // 获取该方法的所在类的 Class 对象
            Class<?> methodClass = method.getDeclaringClass();
            // 将该方法签名作为 key，方法 Class 对象作为 value put 进 subscriberClassByMethodKey
            Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);

            // 场景：查找方法并添加是从子类向父类的顺序进行的
            // methodClassOld 不为 null 的情况可以是这样的：
            // 方法签名相同，但是 Class 对象不同，新的 Class 对象正常情况下是其父类
            // 不会存在方法签名相同而 Class 对象又是同一个的情况，这属于一个类中有一个以上相同签名的方法，这是不允许的

            // isAssignableFromL: 判断当前的 Class 对象所表示的类，是不是参数中传递的 Class 对象所表示类的父类、超接口、或者是相同的类型。
            // 如果没有旧值返回，或者 methodClassOld 是 methodClass 自己或父类型时，返回 true
            if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
                // 仅在未在子类中找到时添加
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
            // 是否跳过父类的方法查找
            if (skipSuperClasses) {
                // 如果配置了跳过父类的订阅方法查找，就直接返回 null 终止查询
                clazz = null;
            } else {
                // 获取父类的 Class 对象
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
