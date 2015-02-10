package homework1;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Implements a Peterson lock for n threads
 * using a tree structure
 * @author Michael Coughlin
 *
 */
public class Peterson implements Lock
{
	final private ThreadLocal<Integer> THREAD_ID = new ThreadLocal<Integer>(){
		final private AtomicInteger id = new AtomicInteger(0);

		protected Integer initialValue(){
			return id.getAndIncrement();
		}
	};


	//We use AtomicBoolean here.
	//Using boolean would not work because of memory model issues --- 
	// Java memory model is not sequentially consistent. 
	//Using volatile boolean would not work, because there is no way of declaring
	//an array element volatile.
	//logical tree of flags is stored in this array, with the size
	//being that of the number of nodes in the tree
	private AtomicBoolean[] flag;// = new AtomicBoolean[2];
	
	//this array stores a tree containing the victim thread information
	//for each lock. this tree is the size of the number of nodes in the
	//flag tree minus the number of leaf nodes
	private AtomicInteger[] victim;

	//    private volatile int victim;
	
	//this holds the number of threads
	private volatile int numThreads;
	
	//this holds the number of nodes in the logical tree
	private int numNodes; 
	
	//this stores the number of nodes in all but the last level of the tree,
	//e.g. all non leaf nodes in a complete tree
	private int numInternalNodes; 
	
	//stores the height of the tree
	private int treeHeight;

	/**
	 * Constructor for the lock that calculates and allocates the logical
	 * tree structure. This class uses a stored array structure for the tree,
	 * rather than a node based version, as the tree does not need to be
	 * resized once allocated.
	 * 
	 * @param numThreads int number of threads that need to be handled
	 */
	public Peterson(int numThreads){
		this.numThreads = numThreads;
		
		//calculate next power of 2 for the number of threads
		//is needed to calculate the size of a complete tree
		//when the input number of threads is not a power of
		//two
		int nextPower = nextPowerOfTwo(numThreads);
		
		//calculate the tree number of nodes in the tree
		//2*(# leaf nodes - 1) - 1 + (# leaf nodes)
		int binaryTreeSize = (int) (2*Math.pow(2, nextPower - 1) - 1) + numThreads;
		numNodes = binaryTreeSize;
		
		//calculate the number of internal nodes of the tree
		//e.g. the number of nodes other than the last level
		numInternalNodes = (int) Math.floor(numNodes/2);
		
		//allocate logical tree
		flag = new AtomicBoolean[binaryTreeSize];
		for(int i=0 ; i<flag.length ; ++i)
			flag[i] = new AtomicBoolean();
		
		victim = new AtomicInteger[numInternalNodes];
		for(int i = 0; i<victim.length; i++){
			victim[i] = new AtomicInteger();
		}
		
		//calculate the tree height: log base 2 of number of nodes
		treeHeight = (int) (Math.log(Math.pow(2, nextPower))/Math.log(2));
	}

	/**
	 * Calculates the next power of two of an input number,
	 * or returns the current power if the input is a power
	 * of 2:
	 * 
	 * nextPowerOfTwo(2) returns 1
	 * nextPowerOfTwo(2.4) returns 2
	 * 
	 * @param answer int the current answer of answer = 2^x
	 * @return int the result of Ceil(log(answer,2))
	 */
	private int nextPowerOfTwo(int answer) {
		double exponent = Math.log(answer)/Math.log(2);
		if(exponent%1 == 0){
			return (int) exponent;
		} else{
			return (int) Math.ceil(exponent);
		}
	}

	/**
	 * Acquires a lock for the calling thread. Will lead to
	 * busy waiting if there are other threads requesting a
	 * a lock concurrently. The thread receives the lock
	 * once this method returns.
	 */
	public void lock() {
		//acquire the thread's ID
		int threadID = THREAD_ID.get() % numThreads;
		
		//acquire the index in the logical tree of the
		//thread's leaf node
		int leafIndex = goToLeaf(threadID, false);
		
		//start at the index of the current thread's
		//leaf node
		int currentIndex = leafIndex;
		
		//iterate through each level of the tree, until the top level is reached
		//once the thread reaches the top then the final lock is granted
		for(int currentLevel = treeHeight; currentLevel > 0; currentLevel--){
			//find the array index in the tree of this node's parent
			int parentIndex = (int) Math.floor((currentIndex - 1)/2);
			//find the array index of this current node's sibling, if
			//it exists
			int siblingIndex = 2*parentIndex + 1 + (currentIndex)%2;
			//set this thread's flag to true, signaling it wants
			//access to this parent's lock
			flag[currentIndex].set(true);
			//set this thread as the victim for this lock
			victim[parentIndex].set(threadID);
			//simple check if there is a sibling. this can only occur on the bottom
			//of the tree when the number of threads is not an even power of two.
			//this will then only occur on the farthest right leaf nodes on this level
			//which are always stored as the elements or the array
			//if there are no siblings, then the thread has no contention on this 
			//level of the tree and may proceed to the next level
			if(siblingIndex < flag.length){
				//if there are siblings (most likely, and certain if above the bottom level),
				//busy wait until the other sibling has released the lock at this level
				while( flag[siblingIndex].get() && victim[parentIndex].get() == threadID) {};
			}
			//once the lock has been released, move to the parent node at the next level
			currentIndex = parentIndex;
		}
//		System.out.println("lock by thread: " + threadID);
	}
	
	/*
    private void lock_single(){
    	int i = THREAD_ID.get() % 2;
        int j = 1 - i;
        flag [i].set(true); // I am interested
        victim = i ; // you go first
        while ( flag[j].get() && victim == i) {}; // wait
    }
	 */

	/**
	 * This method performs a binary search of the logical tree to find
	 * the index in the array of a leaf node whose thread ID is proved.
	 * This method will also release locks starting at the root and
	 * going to the thread ID's leaf node if the boolean flag is set.
	 * 
	 * Precondition: the leaf nodes are ordered such that the left-most
	 * child is the thread with ID 0, and the right-most leaf node is
	 * the thread with ID threads-1. Otherwise, the binary search is
	 * not valid, as the logical tree does not store thread IDs directly.
	 * 
	 * @param threadID int ID of the thread leaf node being searched for
	 * @param resetFlag boolean flag indicated whether to reset locks
	 * 					at each stage
	 * @return int array index in the logical tree of the thread's leaf node 
	 */
	private int goToLeaf(int threadID, boolean resetFlag) {
		double max = (int) (Math.pow(2, treeHeight) - 1);
		double min = 0;
		double middle = max / 2;
		int currentIndex = 0;

		for(int  currentLevel = 0; currentLevel < treeHeight; currentLevel++){
			if(threadID > middle){
				min = middle;
				middle = (max - min) / 2 + min;
				currentIndex = 2*currentIndex + 2;
			} else if(threadID < middle){
				max = middle;
				middle = (max - min) /2 + min;
				currentIndex = 2*currentIndex + 1;
			}
			if(resetFlag){
				flag[currentIndex].set(false);
			}
		}
		return currentIndex;
	}

	/**
	 * Releases locks in the logical tree allocated to the
	 * calling thread. The thread id is determined with the
	 * THREAD_ID.get() method and the goToLeaf function is
	 * used to reset locks from the root to the leaf. 
	 */
	public void unlock(){
		int threadID = THREAD_ID.get() % numThreads;
		goToLeaf(threadID, true);
//		System.out.println("unlock by thread: " + threadID);
	}

	/*
	public void unlock_single() {
		int i = THREAD_ID.get() % 2;
		flag[i].set(false); // I am not interested
	}*/

	// Any class implementing Lock must provide these methods
	public Condition newCondition() {
		throw new java.lang.UnsupportedOperationException();
	}
	public boolean tryLock(long time,
			TimeUnit unit)
					throws InterruptedException {
		throw new java.lang.UnsupportedOperationException();
	}
	public boolean tryLock() {
		throw new java.lang.UnsupportedOperationException();
	}
	public void lockInterruptibly() throws InterruptedException {
		throw new java.lang.UnsupportedOperationException();
	}
}


