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


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 订阅注解，用于标记订阅者方法
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Subscribe {

    /**
     * 线程模式，默认值 {@link ThreadMode#POSTING}
     */
    ThreadMode threadMode() default ThreadMode.POSTING;

    /**
     * 是否是黏性事件，默认值为 false
     * 如果为 true，则将最近的粘性事件（使用 {@link EventBus#postSticky(Object)} 发布）传递给该订阅者（如果事件可用）。
     */
    boolean sticky() default false;

    /**
     * 订阅者优先级影响事件传递的顺序。
     * 在同一交付线程 ({@link ThreadMode}) 中，优先级较高的订阅者将在其他优先级较低的订阅者之前收到事件。
     * 默认优先级为 0。
     * 注意：优先级不影响具有不同 {@link ThreadMode} 的订阅者之间的传递顺序！
     */
    int priority() default 0;
}

