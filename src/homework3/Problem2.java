package homework3;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;


public class Problem2 {
	
	static private volatile int completedThreads = 0;
	static private AtomicBoolean locked = new AtomicBoolean(false);
	static private volatile int[] barrierArray = new int[16];
	static private long startTime = 0L;
	static private long endTime = 0L;
	private static String fileName;

	public static void main(String[] args) {
		if(args.length != 1){
			System.out.println("Requires barrier mode: true for test and test and set, false for array");
			return;
		}
		//arg[0]: true is to use test & test & set, false use array barrier
		boolean mode = Boolean.parseBoolean(args[0]);
		if(mode){
			System.out.println("Beginning test and test-and-set option");
			fileName = "testTestSet.csv";
		} else{
			System.out.println("Beginning barrier array option");
			fileName = "barrierArray.csv";
			for(int i = 0; i<16; i++){
				barrierArray[i] = 0;
			}
		}
		test(mode);
	}
	
	private static void test(boolean mode) {
		Thread[] threads = new Thread[16];
		for(int i = 0; i<16; i++){
			//create threads
			Thread currentThread;
			if(mode){
				currentThread = new Thread() {
					public void run(){
						testTestSetThreadCode();
					}
				};
			} else{
				final int threadID = i;
				currentThread = new Thread() {
					public void run(){
						barrierArrayThreadCode(threadID);
					}
				};
			}
			threads[i] = currentThread;
		}
		startTime = System.nanoTime();
		for(int i = 0; i<16; i++){
			threads[i].start();
			
		}
		for(int i = 0; i<16; i++){
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		endTime = System.nanoTime();
		double elapsedTime = ((double)(endTime - startTime)) * 1.0E-9;
		System.out.println("Elapsed time: " + elapsedTime + " s");
		writeToFile(elapsedTime, fileName, true);
	}
	
	private static void writeToFile(double elapsedTime, String outputFileName, boolean appendMode) {
		File outFile = new File(outputFileName);
		if(!outFile.exists()){
			try {
				outFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			FileWriter writer = new FileWriter(outFile, appendMode);
			writer.write(elapsedTime+System.lineSeparator());
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void barrierArrayThreadCode(int ThreadID){
		//execute foo()
		try {
			foo();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		//barrier
		if(ThreadID == 0){
			//thread 0 sets first index, waits on last
			barrierArray[0] = 1;
			while(barrierArray[15] != 1){}
		} else{
			//other threads wait on index ID-1, set index ID, wait on last
			while(barrierArray[ThreadID - 1] != 1){}
			barrierArray[ThreadID] = 1;
			while(barrierArray[15] != 1){}
		}
		//when last thread completes, enter critical section
		try {
			bar();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private static void testTestSetThreadCode(){
		//execute foo()
		try {
			foo();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		//spin while appears locked
		while(locked.get()){}
		//check the lock, lock if it is unlocked
		while(!locked.compareAndSet(false, true)){}
		//increment counter
		completedThreads++;
		locked.set(false);
		//wait while counter is is less then number of threads
		while(completedThreads < 16){}
		
		//enter critical section
		//execute bar()
		try {
			bar();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void bar() throws InterruptedException{
		Thread.sleep(20);
	}
	
	private static void foo() throws InterruptedException{
		Thread.sleep(20);
	}
}
