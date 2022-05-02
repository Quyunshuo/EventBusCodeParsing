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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

/**
 * Handle 发布事件程序
 * 实现了 Poster 接口，这就是一个普通的 Handler，只是它的 Looper 使用的是主线程的 「Main Looper」，可以将消息分发到主线程中
 * enqueue() 方法并不会每次都发送 Message 激活 handleMessage()，这是通过 handlerActive 标志位进行控制的
 * 那么 enqueue() 中那些没有被消费的事件该怎么消费呢？答案是 handleMessage() 中的 while 死循环
 * 但是为了避免一直在死循环中处理事件影响主线程的性能，又设置了一个超时时间，一旦执行了超时了
 * 那么再发送一个 Message 并且退出，那么 Handler 的机制可以保证过会儿又能进入到 handleMessage() 方法中继续处理队列中的事件
 * 核心还是通过反射进行调用的，这儿也能看出订阅方法的执行是在主线程中的。但是由于 enqueue() 的存在，订阅与发布是异步的，订阅的消费不会阻塞发布
 */
public class HandlerPoster extends Handler implements Poster {

    private final PendingPostQueue queue;
    // 处理消息最大间隔时间 默认10ms
    private final int maxMillisInsideHandleMessage;
    private final EventBus eventBus;
    private boolean handlerActive;

    public HandlerPoster(EventBus eventBus, Looper looper, int maxMillisInsideHandleMessage) {
        super(looper);
        this.eventBus = eventBus;
        this.maxMillisInsideHandleMessage = maxMillisInsideHandleMessage;
        queue = new PendingPostQueue();
    }

    public void enqueue(Subscription subscription, Object event) {
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        synchronized (this) {
            queue.enqueue(pendingPost);
            if (!handlerActive) {
                handlerActive = true;
                if (!sendMessage(obtainMessage())) {
                    throw new EventBusException("Could not send handler message");
                }
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        boolean rescheduled = false;
        try {
            long started = SystemClock.uptimeMillis();
            while (true) {
                PendingPost pendingPost = queue.poll();
                if (pendingPost == null) {
                    synchronized (this) {
                        // Check again, this time in synchronized
                        pendingPost = queue.poll();
                        if (pendingPost == null) {
                            handlerActive = false;
                            return;
                        }
                    }
                }
                eventBus.invokeSubscriber(pendingPost);
                long timeInMethod = SystemClock.uptimeMillis() - started;
                if (timeInMethod >= maxMillisInsideHandleMessage) {
                    if (!sendMessage(obtainMessage())) {
                        throw new EventBusException("Could not send handler message");
                    }
                    rescheduled = true;
                    return;
                }
            }
        } finally {
            handlerActive = rescheduled;
        }
    }
}