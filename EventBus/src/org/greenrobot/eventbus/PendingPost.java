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

import java.util.ArrayList;
import java.util.List;

/**
 * {@link PendingPostQueue} 的元素抽象
 * 该类主要是将 事件、订阅关系、包装为一个类，并且做了对象的缓存池进行复用
 */
public final class PendingPost {

    // 依旧是一个对象复用池
    private final static List<PendingPost> pendingPostPool = new ArrayList<PendingPost>();
    // 对应的事件
    Object event;
    // 订阅者方法包装类
    Subscription subscription;
    // 下一个元素
    PendingPost next;

    /**
     * 唯一构造
     *
     * @param event        Object 事件
     * @param subscription Subscription 订阅者方法包装类
     */
    private PendingPost(Object event, Subscription subscription) {
        this.event = event;
        this.subscription = subscription;
    }

    /**
     * 获取一个 PendingPost
     * 可能是复用的对象，也可能是新建的对象，这取决于对象池
     *
     * @param subscription Subscription 订阅者方法包装类
     * @param event        Object 事件
     * @return PendingPost
     */
    public static PendingPost obtainPendingPost(Subscription subscription, Object event) {
        // 加锁，监视器为 pendingPostPool 对象池
        synchronized (pendingPostPool) {
            // 获取对象池的长度
            int size = pendingPostPool.size();
            // 如果对象池长度大于0，证明有可以被复用的缓存对象
            if (size > 0) {
                // 取出一个对象并对其赋值
                PendingPost pendingPost = pendingPostPool.remove(size - 1);
                pendingPost.event = event;
                pendingPost.subscription = subscription;
                pendingPost.next = null;
                // 返回此复用的对象
                return pendingPost;
            }
        }
        // 到此步骤表示没有可以被复用的对象，于是就进行创建新的实例
        return new PendingPost(event, subscription);
    }

    /**
     * 释放 PendingPost 并加入到对象池中准备下一个事件被使用
     *
     * @param pendingPost PendingPost
     */
    static void releasePendingPost(PendingPost pendingPost) {
        // 重置状态
        pendingPost.event = null;
        pendingPost.subscription = null;
        pendingPost.next = null;
        // 加锁 监视器为 pendingPostPool 对象池
        synchronized (pendingPostPool) {
            // 不要让池无限增长，当长度小于 10000 时才进行复用，否则直接丢弃
            if (pendingPostPool.size() < 10000) {
                pendingPostPool.add(pendingPost);
            }
        }
    }

}