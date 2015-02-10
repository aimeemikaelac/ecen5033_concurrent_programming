
package homework1;


public class PetersonTest {
	private int THREADS;
	private int COUNT;
	private int PER_THREAD;
	Thread[] thread;
	int counter = 0;

	Peterson instance;

	/**
	 * Basic constructor that constructs the class fields.
	 * There is no need for static variables, so I set them in
	 * the constructor in order to support different numbers of
	 * threads.
	 * @param numThreads int number of threads to be used
	 */
	public PetersonTest(int numThreads){
		this.THREADS = numThreads;
		instance = new Peterson(THREADS);
		this.COUNT = 64*1024;
		this.PER_THREAD = COUNT/THREADS;
		this.thread = new Thread[THREADS];
	}

	public void testParallel() throws Exception {
//		System.out.println("test parallel");
		//      ThreadID.reset();
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
//					System.out.println(counter);
				} finally {
					instance.unlock();
				}
			}
		}
	}

	/**
	 * Main class for function. I added an argument to specify the number
	 * of threads to use from the command line.
	 * @param args String[] list of arguments. Only one expected - # of threads
	 */
	public static void main(String[] args) {
		PetersonTest mpt = new PetersonTest(Integer.parseInt(args[0]));
		try {
			mpt.testParallel();
		}
		catch (Exception e) {}   
	}
}
