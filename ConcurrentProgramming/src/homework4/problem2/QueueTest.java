package homework4.problem2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class QueueTest {
	
	public static void main(String[] args) {
		int numTests = 1000;
		double singleRunningTotal = 0;
		double[] singleData = new double[numTests];
		double allRunningTotal = 0;
		double[] allData = new double[numTests];
		
		for(int i = 0; i<numTests; i++){
			singleData[i] = singleWakeQueueTest();
			singleRunningTotal += singleWakeQueueTest();
		}
		
		for(int i = 0; i<numTests; i++){
			allData[i] = allWakeQueueTest();
			allRunningTotal += allWakeQueueTest();
		}
		
		double singleAverage = singleRunningTotal / ((double)numTests);
		
		double allAvergage = allRunningTotal / ((double)numTests);
		
		System.out.println("Averages for 20 tests:");
		System.out.println("Single-wake average runtime: "+singleAverage+" ms");
		System.out.println("Single-wake runtime standard deviation: "+StandardDeviation.standardDeviationCalculate(singleData)+" ms");
		System.out.println("All-wake average runtime: "+allAvergage+" ms");
		System.out.println("All-wake runtime standard deviation: "+StandardDeviation.standardDeviationCalculate(allData)+" ms");
	}

	private static double allWakeQueueTest() {
		ExecutorService executor = Executors.newCachedThreadPool();
		
		final QueueAllWake<Integer> allAwake = new QueueAllWake<Integer>(5);
		
		Runnable allQueueEnqueuer = new Runnable(){

			@Override
			public void run() {
				for(int i = 0; i<10; i++){
					allAwake.enq(i);
				}
			}
			
		};
		
		Runnable allQueueDeqeuer = new Runnable(){

			@Override
			public void run() {
				for(int i = 0; i<10; i++){
					allAwake.deq();
				}
			}
			
		};
		
		long start = System.nanoTime();
		
		for(int i = 0; i<5; i++){
			executor.submit(allQueueDeqeuer);
			executor.submit(allQueueEnqueuer);
		}
		
		executor.shutdown();
		
		try {
			executor.awaitTermination(5, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		long finished = System.nanoTime();
		
		double runtimeMilliseconds = ((double)(finished-start)) * 1E-6;
		
		return runtimeMilliseconds;
		
	}

	private static double singleWakeQueueTest() {
		ExecutorService executor = Executors.newCachedThreadPool();
		
		final QueueSingleWake<Integer> singleAwake = new QueueSingleWake<Integer>(5);
		
		
		Runnable singleQueueEnqueuer = new Runnable() {

			@Override
			public void run() {
				for(int i = 0; i<10; i++){
					singleAwake.enq(i);
				}
			}
			
		};
		
		Runnable singleQueueDequeuer = new Runnable(){

			@Override
			public void run() {
				for(int i = 0; i<10; i++){
					singleAwake.deq();
				}
			}
			
		};
		
		long start = System.nanoTime();
		
		for(int i = 0; i<5; i++){
			executor.submit(singleQueueDequeuer);
			executor.submit(singleQueueEnqueuer);
		}
		
		executor.shutdown();
		
		try {
			executor.awaitTermination(5, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		long finished = System.nanoTime();
		
		double runtimeMilliseconds = ((double)(finished-start)) * 1E-6;
		
		return runtimeMilliseconds;
	}

}
