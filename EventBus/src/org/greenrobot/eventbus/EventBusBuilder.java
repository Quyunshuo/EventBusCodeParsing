/*
 * Copyright (C) 2012-2020 Markus Junginger, greenrobot (http://greenrobot.org)
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

import org.greenrobot.eventbus.android.AndroidComponents;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 使用自定义参数创建 EventBus 实例，还允许安装自定义的默认 EventBus 实例。
 * 使用 {@link EventBus#builder()} 创建一个新的构建器。
 */
@SuppressWarnings("unused")
public class EventBusBuilder {
    // 私有线程池
    private final static ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    // 订阅函数执行有异常时，打印异常信息
    boolean logSubscriberExceptions = true;
    // 事件无匹配订阅函数时，打印信息
    boolean logNoSubscriberMessages = true;
    // 订阅函数执行有异常时，发布 SubscriberExceptionEvent 事件
    boolean sendSubscriberExceptionEvent = true;
    // 事件无匹配订阅函数时，发布NoSubscriberEvent
    boolean sendNoSubscriberEvent = true;
    // 如果订阅者方法执行有异常时，抛出 SubscriberException
    boolean throwSubscriberException;
    // 事件继承
    boolean eventInheritance = true;
    // 是否忽略生成的索引 默认值为 false
    boolean ignoreGeneratedIndex;
    // 是否进行严格的方法验证 默认值为 false
    boolean strictMethodVerification;
    // 公开线程池
    ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;
    // 跳过类的方法验证
    List<Class<?>> skipMethodVerificationForClasses;

    // 订阅者索引集合 SubscriberInfoIndex 是注解处理器生成的索引类的抽象接口
    // 该属性只有在调用 addIndex(SubscriberInfoIndex index) 添加索引类的时候才会被初始化赋值
    List<SubscriberInfoIndex> subscriberInfoIndexes;
    // 日志处理程序
    Logger logger;
    // 主线程支持
    MainThreadSupport mainThreadSupport;

    EventBusBuilder() {
    }

    /** Default: true */
    public EventBusBuilder logSubscriberExceptions(boolean logSubscriberExceptions) {
        this.logSubscriberExceptions = logSubscriberExceptions;
        return this;
    }

    /** Default: true */
    public EventBusBuilder logNoSubscriberMessages(boolean logNoSubscriberMessages) {
        this.logNoSubscriberMessages = logNoSubscriberMessages;
        return this;
    }

    /** Default: true */
    public EventBusBuilder sendSubscriberExceptionEvent(boolean sendSubscriberExceptionEvent) {
        this.sendSubscriberExceptionEvent = sendSubscriberExceptionEvent;
        return this;
    }

    /** Default: true */
    public EventBusBuilder sendNoSubscriberEvent(boolean sendNoSubscriberEvent) {
        this.sendNoSubscriberEvent = sendNoSubscriberEvent;
        return this;
    }

    /**
     * Fails if an subscriber throws an exception (default: false).
     * <p/>
     * Tip: Use this with BuildConfig.DEBUG to let the app crash in DEBUG mode (only). This way, you won't miss
     * exceptions during development.
     */
    public EventBusBuilder throwSubscriberException(boolean throwSubscriberException) {
        this.throwSubscriberException = throwSubscriberException;
        return this;
    }

    /**
     * By default, EventBus considers the event class hierarchy (subscribers to super classes will be notified).
     * Switching this feature off will improve posting of events. For simple event classes extending Object directly,
     * we measured a speed up of 20% for event posting. For more complex event hierarchies, the speed up should be
     * greater than 20%.
     * <p/>
     * However, keep in mind that event posting usually consumes just a small proportion of CPU time inside an app,
     * unless it is posting at high rates, e.g. hundreds/thousands of events per second.
     */
    public EventBusBuilder eventInheritance(boolean eventInheritance) {
        this.eventInheritance = eventInheritance;
        return this;
    }


    /**
     * 为 EventBus 提供一个自定义线程池，用于异步和后台事件传递。
     * 这是一个可以破坏事情的高级设置：确保给定的 ExecutorService 不会卡住以避免未定义的行为。
     */
    public EventBusBuilder executorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    /**
     * Method name verification is done for methods starting with onEvent to avoid typos; using this method you can
     * exclude subscriber classes from this check. Also disables checks for method modifiers (public, not static nor
     * abstract).
     */
    public EventBusBuilder skipMethodVerificationFor(Class<?> clazz) {
        if (skipMethodVerificationForClasses == null) {
            skipMethodVerificationForClasses = new ArrayList<>();
        }
        skipMethodVerificationForClasses.add(clazz);
        return this;
    }

    /** Forces the use of reflection even if there's a generated index (default: false). */
    public EventBusBuilder ignoreGeneratedIndex(boolean ignoreGeneratedIndex) {
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
        return this;
    }

    /** 启用严格的方法验证（默认值：false） */
    public EventBusBuilder strictMethodVerification(boolean strictMethodVerification) {
        this.strictMethodVerification = strictMethodVerification;
        return this;
    }

    /** Adds an index generated by EventBus' annotation preprocessor. */
    public EventBusBuilder addIndex(SubscriberInfoIndex index) {
        if (subscriberInfoIndexes == null) {
            subscriberInfoIndexes = new ArrayList<>();
        }
        subscriberInfoIndexes.add(index);
        return this;
    }

    /**
     * 为所有 EventBus 日志设置特定的日志处理程序
     * 默认情况下，所有日志记录都是通过 Android 上的 {@code android.util.Log} 或 JVM 上的 System.out。
     */
    public EventBusBuilder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    /**
     * 获取 Logger
     */
    Logger getLogger() {
        if (logger != null) {
            return logger;
        } else {
            return Logger.Default.get();
        }
    }

    /**
     * 获取主线程支持
     */
    MainThreadSupport getMainThreadSupport() {
        if (mainThreadSupport != null) {
            return mainThreadSupport;
        } else if (AndroidComponents.areAvailable()) {
            // 如果有 Android 组件就返回默认的 Android 主线程支持
            return AndroidComponents.get().defaultMainThreadSupport;
        } else {
            return null;
        }
    }

    /**
     * Installs the default EventBus returned by {@link EventBus#getDefault()} using this builders' values. Must be
     * done only once before the first usage of the default EventBus.
     *
     * @throws EventBusException if there's already a default EventBus instance in place
     */
    public EventBus installDefaultEventBus() {
        synchronized (EventBus.class) {
            if (EventBus.defaultInstance != null) {
                throw new EventBusException("Default instance already exists." +
                        " It may be only set once before it's used the first time to ensure consistent behavior.");
            }
            EventBus.defaultInstance = build();
            return EventBus.defaultInstance;
        }
    }

    /** Builds an EventBus based on the current configuration. */
    public EventBus build() {
        return new EventBus(this);
    }

}
