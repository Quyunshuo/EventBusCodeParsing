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

/**
 * “主”线程的接口，可以是任何你喜欢的。通常在 Android 上，使用 Android 的主线程。
 */
public interface MainThreadSupport {
    /**
     * 当前是否是主线程
     */
    boolean isMainThread();

    /**
     * 创建事件发布器
     */
    Poster createPoster(EventBus eventBus);
}
