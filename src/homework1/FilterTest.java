
package homework1;


public class FilterTest  {
  private int THREADS;
  private int COUNT;
  private int PER_THREAD;
  Thread[] thread;
  volatile int counter;
  
  Filter instance;
  java.util.concurrent.locks.Lock lock = new java.util.concurrent.locks.ReentrantLock();
  
  public FilterTest(int numThreads){
	  this.THREADS = numThreads;
	  this.COUNT = 64*1024;
	  this.PER_THREAD = COUNT / THREADS;
	  this.thread = new Thread[THREADS];
	  counter = 0;
	  instance = new Filter(THREADS);
  }
  
  public void testParallel() throws Exception {
    System.out.println("test parallel");
    //ThreadID.reset();
    for (int i = 0; i < THREADS; i++) {
      thread[i] = new MyThread();
    }
    for (int i = 0; i < THREADS; i++) {
      thread[i].start();
    }
    for (int i = 0; i < THREADS; i++) {
      thread[i].join();
    }
    if (counter != COUNT) {
	System.out.println("Wrong! " + counter + " " + COUNT);
    }
  }
  
  class MyThread extends Thread {
    public void run() {
      for (int i = 0; i < PER_THREAD; i++) {
	  instance.lock();
        try {
          counter = counter + 1;
//          System.out.println(counter);
        } finally {
	    instance.unlock();
        }
      }
      //System.out.println("ThreadID: "+ThreadID.get());
    }
  }

  public static void main(String[] args) {
    FilterTest mft = new FilterTest(Integer.parseInt(args[0]));
    try {
      mft.testParallel();
    }
    catch (Exception e) {}   
  }

}
