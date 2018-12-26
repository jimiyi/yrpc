package com.yiwei.middleware.yrpc.lock;

public abstract class Aqser implements java.io.Serializable {
    private static final long serialVersionUID = 1281595678908459731L;

    protected Aqser() {
    }

    static final class Node {
        /**
         * 共享锁的节点
         */
        static final Node SHARED = new Node();
        /**
         * 独占锁的节点
         */
        static final Node EXCLUSIVE = null;

        /**
         * 线程的状态：取消
         */
        static final int CANCELLED = 1;

        /**
         * 线程的状态：表示后续线程需要被激活
         */
        static final int SIGNAL = -1;

        /**
         * 线程状态：表示当前线程在等待某个条件
         */
        static final int CONDITION = -2;

        static final int PROPAGATE = -3;

        /**
         * 线程状态
         */
        volatile int waitStatus;

        /**
         * 上一个节点
         */
        volatile Node prev;

        /**
         * 下一个节点
         */
        volatile Node next;

        /**
         * 保存在这个节点上的线程信息
         */
        volatile Thread thread;

        Node nextWaiter;

        /**
         * 判断是否在共享模式下
         * @return
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 返回前一个节点，如果为空则抛出NullPointerException
         * @return
         * @throws NullPointerException
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }
}
