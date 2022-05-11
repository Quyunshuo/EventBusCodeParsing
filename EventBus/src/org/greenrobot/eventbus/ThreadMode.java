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
 * 每个订阅者方法都有一个线程模式，它决定了 EventBus 将在哪个线程中调用该方法。 EventBus 独立于发布线程处理线程。
 *
 * @see EventBus#register(Object)
 */
public enum ThreadMode {
    /**
     * 这是默认设置。事件交付是同步完成的，一旦发布完成，所有订阅者都将被调用。
     * 此线程模式意味着最小的开销，因为它完全避免了线程切换。
     * 因此，对于已知无需主线程的非常短的时间完成的简单任务，这是推荐的模式。
     * 使用此模式的事件处理程序应该快速返回，以避免阻止发布线程，该线程可能是主线程。
     */
    POSTING,

    /**
     * 在 Android 上, 订阅者将在 Android 的主线程（UI 线程）中调用. 如果发布线程是主线程, 订阅者方法将被直接调用, 阻塞发布线程. 否则，事件将排队等待传递（非阻塞）。
     * 使用这种模式的订阅者必须快速返回以避免阻塞主线程。
     * 如果不在 Android 上，其行为与 {@link #POSTING} 相同。
     */
    MAIN,

    /**
     * 在 Android 上，订阅者将在 Android 的主线程（UI 线程）中被调用。与 {@link #MAIN} 不同，该事件将始终排队等待传递。这确保了 post 调用是非阻塞的。
     * 这给了事件处理一个更严格、更一致的顺序（因此名为MAIN_ORDERED）。
     * 例如，如果您在具有主线程模式的事件处理程序中发布另一个事件，
     * 则第二个事件处理程序将在第一个事件处理程序之前完成（因为它是同步调用的-将其与方法调用进行比较）。
     * 使用MAIN_ORDERED，第一个事件处理程序将完成，然后第二个事件处理程序将在稍后时间点调用（一旦主线程具有容量）。
     * 使用此模式的事件处理程序必须快速返回，以避免阻止主线程
     * 如果不在 Android 上，其行为与 {@link #POSTING} 相同。
     */
    MAIN_ORDERED,

    /**
     * 在 Android 上，订阅者将在后台线程中调用。如果发布线程不是主线程，订阅者方法将直接在发布线程中调用。
     * 如果发布线程是主线程，EventBus 使用单个后台线程，它将按顺序传递其所有事件。
     * 使用此模式的订阅者应尽量快速返回以避免阻塞后台线程。
     * 如果不在 Android 上，则始终使用后台线程。
     */
    BACKGROUND,

    /**
     * 这总是独立于发布线程和主线程。发布事件永远不会等待使用此模式的事件处理程序方法。
     * 如果事件处理程序方法的执行可能需要一些时间，例如网络访问，则应使用此模式。
     * 避免同时触发大量长期运行的异步处理程序方法，以限制并发线程的数量。
     * EventBus使用线程池从已完成的异步事件处理程序通知中高效地重用线程。
     */
    ASYNC
}