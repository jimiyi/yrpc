package com.yiwei.middleware.yrpc.lock;

import sun.misc.Unsafe;

import java.util.concurrent.locks.LockSupport;

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

        /**
         * 表示后续的节点是什么类型的节点，独占或者共享
         */
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
            if (p == null) {
                throw new NullPointerException();
            } else {
                return p;
            }
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
     * CAS 的方式，设置Aqser同步器的state
     *
     * @param expect
     * @param update
     * @return
     */
    protected final boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    /**
     * 采用自旋来取代线程切换，提高相应速度，默认自旋时间1000纳秒
     */
    static final long spinForTimeoutThreshold = 1000L;

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

    /**
     * CAS 的方式，设置双向链表的tail
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS 的方式，设置Node对象中的线程状态
     */
    private static final boolean compareAndSetWaitStatus(Node node, int expect, int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }

    /**
     * CAS 的方式，设置Node对象中的next对象
     */
    private static final boolean compareAndSetNext(Node node, Node expect, Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }

    //====================================链表操作========================================

    /**
     * 添加节点
     *
     * @param node
     * @return 返回之前的尾节点
     */
    private Node enq(final Node node) {
        for (; ; ) {
            //取到尾节点，注意这里是多线程环境，可能重复多次才能取到，所以上面加上了无限循环
            Node t = tail;
            //先查看尾节点，如果尾节点为空，则表示整个链表不存在，需要初始化
            if (t == null) {
                if (compareAndSetHead(new Node())) {
                    tail = head;
                }
            } else {
                node.prev = t;
                //设置尾节点，因为是多线程环境下，需要使用cas
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * 根据模式创建锁等待者，锁上分为独占(Node.EXCLUSIVE)和共享(Node.SHARED)两种模式
     *
     * @param mode
     * @return
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        Node pred = tail;
        //如果列表尾部不为空，则将节点加入尾部,直接返回
        if (pred != null) {
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        //如果列表尾部为空，则构造链表
        enq(mode);
        return node;
    }

    /**
     * Sets head of queue to be node, thus dequeuing. Called only by
     * acquire methods.  Also nulls out unused fields for sake of GC
     * and to suppress unnecessary signals and traversals.
     *
     * @param node the node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 唤醒后续节点
     *
     * @param node
     */
    private void unparkSuccessor(Node node) {
        int ws = node.waitStatus;
        if (ws < 0) {
            compareAndSetWaitStatus(node, ws, 0);
        }
        Node s = node.next;
        //如果后续的节点是null，或者状态值是CANCELLED(取消状态，值为1，其他状态都是小于0的)
        if (s == null || s.waitStatus > 0) {
            s = null;
            //从尾节点开始，遍历后续节点，直到找到状态值为非取消状态的
            for (Node t = tail; t != null && t != node; t = t.prev) {
                if (t.waitStatus <= 0) {
                    s = t;
                }

            }
        }
        if (s != null) {
            //唤醒线程
            LockSupport.unpark(s.thread);
        }
    }

    /**
     * 共享锁释放，激活后续节点，并且标识成传播
     */
    private void doReleaseShared() {
        for (; ; ) {
            //从头节点开始释放
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                //SIGNAL表示需要激活后续节点
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) {
                        continue;            // loop to recheck cases
                    }
                    unparkSuccessor(h);
                } else if (ws == 0 &&
                        !compareAndSetWaitStatus(h, 0, Node.PROPAGATE)) {
                    continue;                // loop on failed CAS
                }

            }
            //如果循环中间head变了，则重复循环
            if (h == head) {
                break;
            }
        }
    }

    /**
     * 设置头节点，？？
     *
     * @param node
     * @param propagate
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        //记录旧的head节点，以便后续检查
        Node h = head;
        setHead(node);
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
                (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared()) {
                doReleaseShared();
            }
        }
    }

    //==================锁的获取和释放====================================

    /**
     * 取消正在获取锁的操作
     * @param node
     */
    private void cancelAcquire(Node node) {
        if (node == null) {
            return;
        }
        node.thread = null;
        //获取节点的前序节点
        Node pred = node.prev;
        //遍历前序节点，跳过状态是已取消的前序节点
        while (pred.waitStatus > 0) {
            node.prev = pred = pred.prev;
        }
        //获取到最先一个状态不为取消的前序节点的后续节点
        Node predNext = pred.next;

        //此节点状态设置成取消
        node.waitStatus = Node.CANCELLED;

        //如果这个节点是尾节点，则把尾节点设置成它的前序节点
        if (node == tail && compareAndSetTail(node, pred)) {
            //将最先一个状态不为取消的前序节点的next设置成null
            compareAndSetNext(pred, predNext, null);
        } else {
            int ws;
            if (pred != head &&
                    //由于当前节点取消获取锁，所以pred的后续节点应该被唤醒，所以要设置pred的状态为SIGNAL
                    ((ws = pred.waitStatus) == Node.SIGNAL || (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL)))
                    && pred.thread != null) {
                Node next = node.next;
                //入参node不为null，且它的next节点的状态不是CANCELLED
                if (next != null && next.waitStatus <= 0) {
                    //将pred的next设置成入参node的next，相当于跳过了入参node这个节点
                    compareAndSetNext(pred, predNext, next);
                }
            } else {
                unparkSuccessor(node);
            }
            //help gc？？ 设置node的thread是null，next指向自身
            node.next = node;
        }
    }

    /**
     * Checks and updates status for a node that failed to acquire.
     * Returns true if thread should block. This is the main signal
     * control in all acquire loops.  Requires that pred == node.prev.
     *
     * @param pred
     * @param node
     * @return
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        //首先检查前趋结点的waitStatus位，如果为SIGNAL
        //表示前趋结点会通知它，那么它可以放心大胆地挂起了
        if (ws == Node.SIGNAL) {
            return true;
        }
        //如果前驱节点是取消的节点，则跳过
        if (ws > 0) {
            //跳过所有的取消节点
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * park()会让当前线程进入waiting状态。在此状态下，
     * 有两种途径可以唤醒该线程：1）被unpark()；2）被interrupt()
     *
     * @return
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }

    /**
     * 使线程在等待队列中获取资源，一直获取到资源后才返回。
     * 如果在整个等待过程中被中断过，则返回true，否则返回false。
     *
     * @param node
     * @param arg
     * @return
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            //自旋
            for (; ; ) {
                final Node p = node.predecessor();
                //head下的第一个节点有资格获取资源
                if (p == head && tryAcquire(arg)) {
                    //head永远指向获取资源的节点
                    setHead(node);
                    // setHead中node.prev已置为null，此处再将head.next置为null，
                    // 就是为了方便GC回收以前的head结点。也就意味着之前拿完资源的结点出队了！
                    p.next = null;
                    failed = false;
                    return interrupted;
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 获取独占锁，如果被中断则抛异常
     *
     * @param arg
     * @throws InterruptedException
     */
    private void doAcquireInterruptibly(int arg) throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null;
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }

        }
    }

    private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null;
                    failed = false;
                    return true;
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }
}
