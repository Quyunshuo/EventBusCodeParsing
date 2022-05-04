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
package org.greenrobot.eventbus.meta;

import org.greenrobot.eventbus.ThreadMode;

/**
 * 订阅者方法信息类
 */
public class SubscriberMethodInfo {

    // 方法名
    final String methodName;
    // 该订阅方法的线程模式
    final ThreadMode threadMode;
    // 接收的事件类型 Class 对象
    final Class<?> eventType;
    // 优先级
    final int priority;
    // 是否是黏性事件
    final boolean sticky;

    /**
     * 构造 2 参数
     *
     * @param methodName 方法名
     * @param eventType  接收的事件类型 Class 对象
     */
    public SubscriberMethodInfo(String methodName, Class<?> eventType) {
        this(methodName, eventType, ThreadMode.POSTING, 0, false);
    }

    /**
     * 构造 3 参数
     *
     * @param methodName 方法名
     * @param eventType  接收的事件类型 Class 对象
     * @param threadMode 该订阅方法的线程模式
     */
    public SubscriberMethodInfo(String methodName, Class<?> eventType, ThreadMode threadMode) {
        this(methodName, eventType, threadMode, 0, false);
    }

    /**
     * 构造 5 参数
     *
     * @param methodName 方法名
     * @param eventType  该订阅方法的线程模式
     * @param threadMode 接收的事件类型 Class 对象
     * @param priority   优先级
     * @param sticky     是否是黏性事件
     */
    public SubscriberMethodInfo(String methodName, Class<?> eventType, ThreadMode threadMode,
                                int priority, boolean sticky) {
        this.methodName = methodName;
        this.threadMode = threadMode;
        this.eventType = eventType;
        this.priority = priority;
        this.sticky = sticky;
    }
}