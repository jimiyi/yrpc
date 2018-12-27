package com.yiwei.test;

import org.junit.Test;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AQSTest {
    @Test
    public void test() {
        Lock lock = new ReentrantLock();

        Runnable runnable0 = new ReentrantLockThread(lock);
        Thread thread0 = new Thread(runnable0);
        thread0.setName("线程0");

        Runnable runnable1 = new ReentrantLockThread(lock);
        Thread thread1 = new Thread(runnable1);
        thread1.setName("线程1");

        Runnable runnable2 = new ReentrantLockThread(lock);
        Thread thread2 = new Thread(runnable2);
        thread2.setName("线程2");

        thread0.start();
        thread1.start();
        thread2.start();

        for (; ; ) ;
    }

    private class ReentrantLockThread implements Runnable {

        private Lock lock;

        public ReentrantLockThread(Lock lock) {
            this.lock = lock;
        }

        @Override
        public void run() {
            try {
                lock.lock();
                for (; ; ) ;
            } finally {
                lock.unlock();
            }
        }

    }
}
