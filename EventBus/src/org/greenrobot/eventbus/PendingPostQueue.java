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
 * 待发布队列
 * 其实现使用的是 内存指针 https://www.jianshu.com/p/5bb6595c0b59
 * 解析：
 * <pre>
 * 入队一个元素：obj1
 * PendingPostQueue:
 *   —— head:obj1
 *       —— next:null
 *   —— tail:obj1
 *       —— next:null
 *
 * 入队第二个元素：obj2
 * PendingPostQueue:
 *   —— head:obj1
 *       —— next:obj2
 *                 —— next:null
 *   —— tail:obj2
 *       —— next:null
 *
 * 入队第三个元素：obj3
 * PendingPostQueue:
 *   —— head:obj1
 *       —— next:obj2
 *                 —— next:obj3
 *                           ——next:null
 *   —— tail:obj3
 *       —— next:null
 *
 * 队尾的 next 始终为 null，而队头的 next 当队列 size<1 时，不为 null
 * 队头的 next 指向下一个元素，而下一个元素的 next 也指向再下一个元素，以此类推，直到最后一个元素的 next 为 null
 * </pre>
 */
public final class PendingPostQueue {
    // 头部
    private PendingPost head;
    // 尾部
    private PendingPost tail;

    /**
     * 入队
     *
     * @param pendingPost PendingPost
     */
    public synchronized void enqueue(PendingPost pendingPost) {
        if (pendingPost == null) {
            throw new NullPointerException("null cannot be enqueued");
        }
        // 尾部不为空，证明队列中已存在其他元素
        if (tail != null) {
            tail.next = pendingPost;
            tail = pendingPost;
        } else if (head == null) {
            head = tail = pendingPost;
        } else {
            throw new IllegalStateException("Head present, but no tail");
        }
        notifyAll();
    }

    public synchronized PendingPost poll() {
        PendingPost pendingPost = head;
        if (head != null) {
            head = head.next;
            if (head == null) {
                tail = null;
            }
        }
        return pendingPost;
    }

    public synchronized PendingPost poll(int maxMillisToWait) throws InterruptedException {
        if (head == null) {
            wait(maxMillisToWait);
        }
        return poll();
    }

}
