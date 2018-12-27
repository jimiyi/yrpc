package com.yiwei.middleware.yrpc.lock;

import sun.misc.Unsafe;

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
         *
         * @return
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 返回前一个节点，如果为空则抛出NullPointerException
         *
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

    private transient volatile Node head;
    private transient volatile Node tail;
    /**
     * 同步的状态
     */
    private volatile int state;

    protected final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * unsafe包含了大量底层操作实现，使得java可以直接操作内存
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;
    static {
        try {
            stateOffset = unsafe.objectFieldOffset(Aqser.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset(Aqser.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset(Aqser.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("next"));
        } catch (NoSuchFieldException e) {
            //Error：表示由 JVM 所侦测到的无法预期的错误，由于这是属于 JVM 层次的严重错误，
            // 导致 JVM 无法继续执行，因此，这是不可捕捉到的，无法采取任何恢复的操作，顶多只能显示错误信息。
            throw new Error(e);
        }
    }

    /**
     * CAS 的方式，设置双向链表的head
     */
    private final boolean compareAndSetHead(Node update) {
        /**
         * compareAndSwapObject(Object var1, long var2, Object var3, Object var4)
         * var1 操作的对象
         * var2 操作的对象属性
         * var3 var2与var3比较，相等才更新
         * var4 更新值
         */
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }
}
