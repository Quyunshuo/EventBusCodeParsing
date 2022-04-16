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


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Subscribe {
    /**
     * 线程模型
     */
    ThreadMode threadMode() default ThreadMode.POSTING;

    /**
     * 粘性事件标记
     */
    boolean sticky() default false;

    /**
     * 订阅方法优先级影响事件传递的顺序。
     * 在同一个传递线程（{@link ThreadMode}）中，优先级较高的订户将在优先级较低的其他订户之前接收事件。
     * 默认优先级为0。
     * 注意：优先级*不会*影响具有不同{@link ThreadMode}的订户之间*发送的顺序！
     */
    int priority() default 0;
}