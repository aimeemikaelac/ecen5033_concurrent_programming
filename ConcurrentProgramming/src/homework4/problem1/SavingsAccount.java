package homework4.problem1;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SavingsAccount {
	private volatile double balance;
	private ReentrantLock lock;
	private AtomicInteger numberPriority;
	private Condition minimumBalanceOrdinary;
	
	public SavingsAccount(double startingBalance){
		balance = startingBalance;
		lock = new ReentrantLock();
		minimumBalanceOrdinary = lock.newCondition();
		numberPriority = new AtomicInteger(0);
	}
	
	public void transfer(int k, SavingsAccount reserve){
		lock.lock();
		try{
			//assuming that this withdraw is ordinary, not preferred
			reserve.withdraw(k, false);
			deposit(k);
		} finally{
			lock.unlock();
		}
	}
	
	public double withdraw(double k, boolean priority){
		if(priority){
			numberPriority.getAndIncrement();
		}
		lock.lock();
		try{
			while((!priority && numberPriority.get() > 0) || balance < k){
				try {
//					System.out.println("preparing to wait");
					System.out.println("number of priority waiting: "+numberPriority.get() + " and I am priority ? "+ priority);
					minimumBalanceOrdinary.await();
				} catch (InterruptedException e) {
					System.out.println("Caught interrupted exception when depositing: " + k);
//					lock.unlock();
//					return -1;
				}
			}
//			System.out.println("---------------------------------");
//			System.out.println("current balance before withdraw: "+balance);
//			System.out.println("ammount to withdraw: "+k);
			balance -= k;
//			System.out.println("new balance after withdraw: "+balance);
//			System.out.println("---------------------------------");
			if(priority){
				numberPriority.getAndDecrement();
			}
			minimumBalanceOrdinary.signal();
		} finally{
			lock.unlock();
		}
		return k;
		
	}
	public void deposit(double k){
		lock.lock();
		try{
//			System.out.println("adding balance");
//			System.out.println("+++++++++++++++++++++++++++++++++++++");
//			System.out.println("balance before deposit: "+balance);
//			System.out.println("balance to add: "+k);
			balance += k;
//			System.out.println("balance after deposit: "+balance);
//			System.out.println("+++++++++++++++++++++++++++++++++++++");
//			System.out.println("signalling all");
			minimumBalanceOrdinary.signal();
//			System.out.println("finished signalling");
		} finally{
			lock.unlock();
		}
	}
	
	public double getbalance(){
		return balance;
	}
	
	private void printBalance() {
		System.out.println("Current balance is: "+balance);
	}
	
	public static void main(String[] args){
		SavingsAccount account = new SavingsAccount(0);
		part1Test(account);
	}

	private static void part1Test(SavingsAccount account) {
		account.printBalance();
		final SavingsAccount depositorReference = account;
		final SavingsAccount printerReference = account;
		final SavingsAccount withdrawerReference = account;
		ExecutorService accountService = Executors.newCachedThreadPool();
		
		Runnable depositerTask = new Runnable() {

			@Override
			public void run() {
				for(int i = 0; i<10; i++){
					depositorReference.deposit(50);
//					System.out.println("Deposited 50");
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		Runnable printTask = new Runnable() {

			@Override
			public void run() {
				for(int i = 0; i<10; i++){
					printerReference.printBalance();
//					System.out.println("printer number "+i);
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		Runnable withdrawalTaskOrdinary = new Runnable() {

			@Override
			public void run() {
				for(int i = 0; i<10; i++){
					withdrawerReference.withdraw(50, false);
//					System.out.println("Withdrew 50");
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		
		Runnable withdrawalTaskPriority = new Runnable() {

			@Override
			public void run() {
				for(int i = 0; i<10; i++){
					withdrawerReference.withdraw(50, true);
//					System.out.println("Withdrew 50");
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		
		for(int i = 0; i<10; i++){
			accountService.submit(depositerTask);
			accountService.submit(depositerTask);
			accountService.submit(printTask);
			accountService.submit(withdrawalTaskOrdinary);
			accountService.submit(withdrawalTaskPriority);
		}
		
		accountService.shutdown();
		try {
			accountService.awaitTermination(5, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		System.out.println("final balance--------------------------------------------");
		account.printBalance();

		accountService = Executors.newCachedThreadPool();
		
		for(int i = 0; i<10; i++){
			accountService.submit(depositerTask);
			accountService.submit(depositerTask);
			accountService.submit(printTask);
			accountService.submit(withdrawalTaskOrdinary);
			accountService.submit(withdrawalTaskPriority);
		}
		
		accountService.shutdown();
		try {
			accountService.awaitTermination(5, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		System.out.println("final balance--------------------------------------------");
		account.printBalance();
	}

}
