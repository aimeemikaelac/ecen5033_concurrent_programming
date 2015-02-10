package multithread_test_directory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadRunner {

	public static void main(String[] args){
		if(args.length != 6){
			printUsage();
			return;
		}
		String testDir = args[0];
		String outfile = args[1];
		int numThreads;
		int numDirectories;
		int numRepitions;
		try{
			numThreads = Integer.parseInt(args[2]);
			numDirectories = Integer.parseInt(args[3]);
			numRepitions = Integer.parseInt(args[5]);
		} catch(NumberFormatException e){
			printUsage();
			return;
		}
		boolean mode = Boolean.parseBoolean(args[4]);
		if(mode){
			System.out.println("Entering single-directory mode");
			for(int i = 0; i<numRepitions; i++){
				testRootMkdir(testDir, numThreads, numDirectories, outfile);
			}
		} else{
			System.out.println("Entering multiple directory mode");
			for(int i = 0; i<numRepitions; i++){
				testMultiDifMkdir(testDir, numThreads, numDirectories, outfile);
			}
		}
//		testMultiDirMkdir(testDir, 10, 10, "testMultiMkdir10-10dirs.csv");
	}

	private static void testMultiDifMkdir(String testDir, int numThreads, int numDirectories, String outfile) {
		runThreadDirectoryTest(testDir, numThreads, numDirectories, outfile, false);
		
	}

	private static void printUsage() {
		System.out.println("Multithread mkdir performance test for a directory");
		System.out.println("Takes 6 arguments");
		System.out.println("argv[0] == test directory");
		System.out.println("argv[1] == Output file. Will be output on CSV format.");
		System.out.println("argv[2] == Number of threads");
		System.out.println("argv[3] == Number of directories to create per thread");
		System.out.println("argv[4] == Mode. True for use a single root directory for all threads. False for directory for each thread.");
		System.out.println("argv[5] == Number of times to repeat");
	}

	private static void testRootMkdir(String testDirectory, int numThreads, int numDirectories, String resultsFile) {
		runThreadDirectoryTest(testDirectory, numThreads, numDirectories, resultsFile, true);
	}

	private static void runThreadDirectoryTest(String testDirectory, int numThreads, int numDirectories, String resultsFile, boolean singleRootDir) {
		CopyOnWriteArrayList<Double> threadOverallAverages = new CopyOnWriteArrayList<Double>();
		for(int i=0; i<numThreads; i++){
			threadOverallAverages.add(new Double(0));
		}
		AtomicLong totalSum = new AtomicLong(0);
		AtomicInteger finishedInteger = new AtomicInteger(0);
		Thread[] threads = new Thread[numThreads];
		File testDir = new File(testDirectory);
		createAndClearDirectory(testDir);
		for(int i = 0; i< numThreads; i++){
			String threadDirectory = testDir.getAbsolutePath(); 
			if(!singleRootDir){
				threadDirectory += "/thread"+i+"testDir";
			}
			threads[i] = new Thread(new TestThread(i, numDirectories, threadOverallAverages, threadDirectory, totalSum, finishedInteger));
		}
		for(Thread thread : threads){
			thread.start();
		}
		
		while(finishedInteger.get() < numThreads){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		double overallAverage = ((double)totalSum.get())/((double)(numThreads*numDirectories));
//		System.out.println("Overall average: "+overallAverage);
		writeToResultsFile(numThreads, resultsFile, threadOverallAverages,
				overallAverage);
	}

	private static void createAndClearDirectory(File currentDirectory) {
		if(currentDirectory.exists()){
			File[] fileList = currentDirectory.listFiles();
			for(File file : fileList){
				if(file.isDirectory()){
					file.delete();
				}
			}
		} else{
			currentDirectory.mkdir();
		}
	}

	private static void writeToResultsFile(int numThreads, String resultsFile,
			CopyOnWriteArrayList<Double> threadOverallAverages,
			double overallAverage) {
		try {
			File outfile = new File(resultsFile);
			boolean fileExists = outfile.exists();
//			System.out.println(fileExists);
//			System.out.println(outfile.getAbsolutePath());
			FileWriter writer = new FileWriter(resultsFile, true);
			String firstRow = "Overall";
			String secondRow = ""+overallAverage/1000.0/1000.0;
			
			for(int i = 0; i<numThreads; i++){
				firstRow += ",Thread"+i;
				secondRow += ","+threadOverallAverages.get(i)/1000.0/1000.0;
			}
			if(!fileExists){
				outfile.createNewFile();
				writer.append(firstRow+"\n");
			}
			writer.append(secondRow+"\n");
			writer.flush();
			writer.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static class TestThread implements Runnable{
		private int threadId;
		private int numDirectories;
		private CopyOnWriteArrayList<Double> averageList;
		private String directoryString;
		private AtomicLong totalSum;
		private AtomicInteger finishedInteger;
		public TestThread(int threadId, int numDirectories, CopyOnWriteArrayList<Double> averageList, String directory, AtomicLong totalSum, AtomicInteger finishedInteger){
			this.threadId = threadId;
			this.numDirectories = numDirectories;
			this.averageList = averageList;
			this.directoryString = directory;
			this.totalSum = totalSum;
			this.finishedInteger = finishedInteger;
		}

		@Override
		public void run() {
			File currentDirectory = new File(directoryString);
			if(currentDirectory.isFile()){
				throw new IllegalArgumentException("test directory has to be a directory");
			}
			Long runningSum = 0L;
			//clear any existing subdirs
//			if(currentDirectory.exists()){
//				File[] fileList = currentDirectory.listFiles();
//				for(File file : fileList){
//					if(file.isDirectory()){
//						file.delete();
//					}
//				}
//			} else{
//				currentDirectory.mkdir();
//			}
			createAndClearDirectory(currentDirectory);
			for(int i = 0; i<numDirectories; i++){
				File currentTestDir = new File(currentDirectory.getAbsoluteFile() + "/thread"+threadId+"test"+i+"dir/");
				//convert to nanoTime() if too coarse of time
//				System.out.println("Creating "+currentTestDir.getAbsolutePath());
				long end;
				long start = System.nanoTime();
				currentTestDir.mkdir();
				end = System.nanoTime();
//				System.out.println("difference: "+(end-start));
				runningSum += (end-start);
			}
			double average = ((double)runningSum)/((double)numDirectories);
			averageList.set(threadId, average);
			totalSum.addAndGet(runningSum);
			finishedInteger.getAndIncrement();
		}
	}
}
