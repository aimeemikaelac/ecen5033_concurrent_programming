package homework4.problem2;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class QueueAllWake<T> {
	private ReentrantLock lock;
	private Condition notFull;
	private Condition notEmpty;
	private T[] items;
	int tail, head, count;
	
	public QueueAllWake(int capacity){
		items = (T[])(new Object[capacity]);
		lock = new ReentrantLock();
		notFull = lock.newCondition();
		notEmpty = lock.newCondition();
		count = 0;
		tail = 0;
		head = 0;
	}
	
	public void enq(T x){
		lock.lock();
		try{
			while(count == items.length){
				try {
					notFull.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			items[tail] = x;
			if(++tail == items.length){
				tail = 0;
			}
			++count;
			notEmpty.signalAll();
		} finally{
			lock.unlock();
		}
	}
	
	public T deq(){
		lock.lock();
		try{
			while(count == 0){
				try {
					notEmpty.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			T x = items[head];
			if(++head == items.length){
				head = 0;
			}
			--count;
			notFull.signalAll();
			return x;
		} finally{
			lock.unlock();
		}
	}
}
