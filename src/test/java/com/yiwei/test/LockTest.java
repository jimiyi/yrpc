package com.yiwei.test;

import org.junit.Test;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockTest {
    private Lock lock = new ReentrantLock();
    int count = 0;

    @Test
    public void test(){
        try {
            lock.lock();
            System.out.println("hello" + count++);
        }finally {
            lock.unlock();
        }
    }
}
