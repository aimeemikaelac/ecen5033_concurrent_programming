/*
	Note: this is only the code that I added to yanc in order to attempt to optimize it.
	The yanc source code has not been open-sourced, so please contact the developers in
	order to see the rest of the system.
*/

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <assert.h>
#include <fnmatch.h>
#define FUSE_USE_VERSION 29
#include <fuse.h>
#include <unistd.h>
#include <time.h>
#include "schema.h"
#include "schemafs.h"
#include "util/util.h"
#include "util/log.h"
#include <pthread.h>

#define MEGEXTRA 1000000
#define N 1000

static SchemaEnt*
_fill(const Schema* schema, SchemaEnt* parent);

void *ThreadDispatcher(void* dispatcherArguments);

void *monitorTask(void* wait);

void *ThreadTask(void *threadNodeArg);

void waitAndCleanup(ThreadNode* rootThreadNode);

typedef struct Queue Queue;
typedef struct QueueItem QueueItem;
typedef struct ThreadNode ThreadNode;

struct Queue{
	//adapted from locked queue from book, chapter 8
	pthread_mutex_t *lock;
	pthread_cond_t *notFull;
	pthread_cond_t *notEmpty;
	QueueItem *head;
	QueueItem *tail;
	int count;
	int keepAlive;
};

struct QueueItem{
	SchemaEnt* schema;
	QueueItem *next;
};

struct ThreadNode{
	int thread_id;
	pthread_t thread;
	ThreadNode *next;
	Queue *queue;
	char* desiredToken;
	SchemaEnt** resultPointer;
	int* wait;
	int* counter;
	pthread_cond_t *condition;
	pthread_mutex_t *lock;
	pthread_mutex_t *counterLock;
	pthread_cond_t *counterCond;
	int comparisons;
};

Queue* queue_init(){
	Queue *queue = NULL;
	queue = malloc(sizeof(Queue));
	if(!queue){
		printf("could not allocate queue\n");
		exit(-1);
	}
	queue->lock = NULL;
	queue->notFull = NULL;
	queue->notEmpty = NULL;
	
	queue->lock = malloc(sizeof(pthread_mutex_t));
	queue->notFull = malloc(sizeof(pthread_cond_t));
	queue->notEmpty = malloc(sizeof(pthread_cond_t));	
	
	if(!queue->lock || !queue->notFull || !queue->notEmpty){
		printf("could not allocate queue lock objects\n");
		exit(-1);
	}
	
	pthread_mutex_init(queue->lock, NULL);
	pthread_cond_init(queue->notEmpty, NULL);
	pthread_cond_init(queue->notFull, NULL);
	
	queue->head = NULL;
	queue->tail = NULL;
	queue->count = 0;
	queue->keepAlive = 1;
	
	return queue;
}

void queue_destroy(Queue *queue){
	QueueItem *temp;
	pthread_mutex_lock(queue->lock);
	queue->count = 0;
	queue->keepAlive = 0;
	while(queue->head){
		temp = queue->head;
		queue->head = queue->head->next;
		free(temp);
		
	}
	
	pthread_mutex_unlock(queue->lock);
	pthread_mutex_destroy(queue->lock);
	pthread_cond_destroy(queue->notEmpty);
	pthread_cond_destroy(queue->notFull);
	
	free(queue);
}

void enq(QueueItem* start, QueueItem* end, int newItems, Queue *queue){
	/*QueueItem *item = NULL;
	item = malloc(sizeof(QueueItem));
	
	if(!item){
		printf("could not allocate queue item\n");
		exit(-1);
	}
	
	item->schema = schema;
	item->next = NULL;
	*/

	pthread_mutex_lock(queue->lock);
	//printf("enqueuing %d items in method with starting count %d\n", newItems, queue->count);	
	if(queue->tail){
		queue->tail->next = start;
	}
	queue->tail = end;
	
	if(!queue->head){
		queue->head = start;
	}
	
	queue->count += newItems;
	
	//printf("ending count %d with head %p start %p tail %p end %p\n", queue->count, queue->head, start, queue->tail, end);
	pthread_cond_signal(queue->notEmpty);
	pthread_mutex_unlock(queue->lock);
	
}

//postcondition: calling threads must free the returned pointer
QueueItem* deq(Queue *queue, int *wait, int id, int numToFetch){
	QueueItem *item;
	QueueItem *last;
	
	if(!queue->keepAlive){
		return NULL;
	}
	int i;
	pthread_mutex_lock(queue->lock);
	while(queue->keepAlive && queue->count <= 0){
		//printf("waiting with flag: %d and count: %d, system id: %d\n", *wait, queue->count, id);
		pthread_cond_wait(queue->notEmpty, queue->lock);
	}
	
	if(!queue->keepAlive){
		//printf("exited with id %d\n", id);
		
		item = NULL;
	} else{
	
		item = queue->head;
		last = item;
		for(i = 0; queue->count>0 && i<numToFetch; i++){
			queue->head = queue->head->next;
	
			queue->count--;
		
		}
		while(last->next != queue->head){
			last = last->next;
		}
		last->next = NULL;
		
		//printf("count: %d, flag: %d thread id: %d numToFetch: %d dequeued: %d and head: %p\n", queue->count, *wait, id, numToFetch, i, queue->head);
		
		if(queue->count == 0){
			queue->tail = NULL;
		}
	}
	
	pthread_mutex_unlock(queue->lock);
	
	return item;
}

//wake all waiting threads to check queue
//called when the answer is found or need to kill threads
void wake_queue(Queue *queue){
	pthread_mutex_lock(queue->lock);
	pthread_cond_broadcast(queue->notEmpty);
	pthread_mutex_unlock(queue->lock);
	//printf("woke queue\n");
}

void toggle_queue(Queue *queue){
	pthread_mutex_lock(queue->lock);
	queue->keepAlive = 0;
	pthread_cond_broadcast(queue->notEmpty);
	pthread_mutex_unlock(queue->lock);
	//printf("toggled queue\n");
}

// This is identical to schema_search() so be sure to duplicate any changes!

SchemaEnt* schemafs_search(SchemaEnt* schema, const char* path_)
{
	char *path, *tok, *_;

	int threadIDs = 0;
	int iterations = 0;
	size_t stacksize;
	
	int *continueToWait;
	int *numRemainingTasks;
	int rc;
	//void* status;
	
	//printf("in search fn\n");
	if (!schema)
		return NULL;
	if (!path_)
		return schema;

	path = strdup(path_);
	assert (path);
	tok  = strtok_r(path, "/", &_);

	if (!tok && path[0] != '/')
		goto fail;
	//printf("tok: %s, path: %s\n", tok, path);
	if (tok == path)
		while (schema->parent != NULL)
			schema = schema->parent;
			
	

	while (tok) {
		//keep track of which iteration we are on
		iterations++;
		
		if (strcmp(tok, "..") == 0) {

			schema = (schema->parent) ? : schema;

		} else if (strcmp(tok, ".") != 0) {

			schema = schema->children;
			/*if(schema){
				printf("root schema name: %s, searchin for: %s\n", schema->name, tok);
			}*/
			//result of thread computation. first thread to find it wins
			SchemaEnt* threadResult = NULL;
			
			//flag that indicates that the answer is still being looked for
			//will be changed when it is found, or all threads are terminated
			
			
			//while (schema) {
			if(schema){	
				//place wait flag on heap
				continueToWait = NULL;
				continueToWait = malloc(sizeof(int));
				numRemainingTasks = NULL;
				numRemainingTasks = malloc(sizeof(int));
				if(!continueToWait || !numRemainingTasks){
					printf("could not allocate wait flag or task counters\n");
					exit(-1);
				}
				*continueToWait = 1;
				*numRemainingTasks = 0;
			
			
				//allocate thread objects on heap, so that multiple threads
				//do not interfere
				pthread_mutex_t *mutexThreadResult = NULL;
				mutexThreadResult = malloc(sizeof(pthread_mutex_t));
				
				pthread_mutex_t *counterLock = NULL;
				counterLock = malloc(sizeof(pthread_mutex_t));

				//pthread_cond_t *threadResultCondition = NULL;
				//threadResultCondition = malloc(sizeof(pthread_cond_t));
				
				pthread_cond_t *counterCondition = NULL;
				counterCondition = malloc(sizeof(pthread_cond_t));

				pthread_attr_t *attr = NULL;
				attr = malloc(sizeof(pthread_attr_t));

				if(!counterLock || !counterCondition || !mutexThreadResult || !attr ){//|| !threadResultCondition){
					printf("could not allocate a thread object\n");
					exit(-1);
				}
			
				//initialize pthread attributes	
				pthread_mutex_init(mutexThreadResult, NULL);
				pthread_mutex_init(counterLock, NULL);
				
				pthread_cond_init(counterCondition, NULL);
					
				pthread_attr_init(attr);

				//increase per-thread stack
				pthread_attr_getstacksize(attr, &stacksize);

				stacksize = sizeof(double)*N*N+MEGEXTRA;
				pthread_attr_setstacksize(attr, stacksize);

				//set thread explicitly as joinable
				pthread_attr_setdetachstate(attr, PTHREAD_CREATE_JOINABLE);
	
					/*if (strcmp(schema->name, tok) == 0)
						break;
					schema = schema->next;*/
				
			
				ThreadNode *rootThreadNode = NULL;
	
				ThreadNode *prevNode = NULL;
			
				ThreadNode *currentNode = NULL;
			
				int numThreads = 0;
				int i;
				int threadComparisons = 40;
			
				//create work queue
				Queue *workQueue = queue_init();
			
				while(schema && *continueToWait){
					if(numThreads >= 40){
						//waitAndCleanup(rootThreadNode);
						//currentNode = rootThreadNode;
						//prevNode = NULL;
						//numThreads = 0;
					} else{
					
						ThreadNode* newNode = NULL;
						newNode = malloc(sizeof(ThreadNode));
		
						if(!newNode){
							printf("could not allocate space for current node\n");
							exit(-1);
						}
						if(numThreads == 0){
							rootThreadNode = newNode;
						}
						currentNode = newNode;
						currentNode->thread_id = threadIDs;
						threadIDs += 1;
						currentNode->next = NULL;
						currentNode->queue = workQueue;
						currentNode->desiredToken = tok;
						currentNode->resultPointer = &threadResult;
						currentNode->wait = continueToWait;
						currentNode->lock = mutexThreadResult;
						currentNode->comparisons = threadComparisons;
						currentNode->counterLock = counterLock;
						currentNode->counterCond = counterCondition;
						currentNode->counter = numRemainingTasks;
						//currentNode->condition = condition;
						if(prevNode){
							prevNode->next = currentNode;
						}
						prevNode = currentNode;

						//dispatch thread
						rc = pthread_create(&(currentNode->thread), attr, ThreadTask, (void *) currentNode);
						if(rc){
							printf("could not create thread %d\n", threadIDs - 1);
							exit(-1);
						}
						
						numThreads++;
					}
					
					QueueItem *end = NULL;
					QueueItem *prev = end;
					QueueItem *start = end;
					for(i = 0; schema && i<3*threadComparisons; i++){
						//printf("in loop with name: %s\n", schema->name);
						end = malloc(sizeof(QueueItem));
						if(!end){
							printf("could not allocate queue item\n");
							exit(-1);
						}
						if(i == 0){
							start = end;	
						}
						if(prev){
							prev->next = end;
						}
						end->schema = schema;
						end->next = NULL;
						prev = end;
						schema = schema->next;
					}
					
					if(i > 0){
						//printf("enqueuing %d items\n",i);
						pthread_mutex_lock(counterLock);
						(*numRemainingTasks) += i;
						pthread_mutex_unlock(counterLock);
						enq(start, end, i, workQueue);
					}
				}
				
				pthread_mutex_lock(counterLock);
				while(*numRemainingTasks > 0 && *continueToWait){
					pthread_cond_wait(counterCondition, counterLock);
				}
				//printf("awoken\n");
				
				pthread_mutex_lock(mutexThreadResult);
				*continueToWait = 0;
				toggle_queue(workQueue);
				wake_queue(workQueue);
				pthread_mutex_unlock(mutexThreadResult);
				
				pthread_mutex_unlock(counterLock);
				
				waitAndCleanup(rootThreadNode);
			
				schema = threadResult;
			
				queue_destroy(workQueue);
			
				//destroy thread attributes
				//pthread_cond_destroy(threadResultCondition);
				pthread_mutex_destroy(mutexThreadResult);
				pthread_mutex_destroy(counterLock);
				pthread_attr_destroy(attr);
	
				free(attr);
				//free(threadResultCondition);
				free(mutexThreadResult);
				free(counterLock);
			
				//free wait flag
				free(continueToWait);
			
			}
			if (!schema){
				//printf("failed to find %s\n", tok);
				goto fail;
			}
		}

		tok = strtok_r(NULL, "/", &_);
	}
	
	free(path);
	return schema;
fail:
	free(path);
	return NULL;
}

void waitAndCleanup(ThreadNode* rootThreadNode){
	//wait on each thread an clean up
	void* status;
	int rc;
	ThreadNode* prevNode = NULL;
	while(rootThreadNode){
		rc = pthread_join(rootThreadNode->thread, &status);
		if(rc){
			printf("could not join thread %d in thread dispatcher cleanup", rootThreadNode->thread_id);
			exit(-1);
		}
		prevNode = rootThreadNode;
		rootThreadNode = rootThreadNode -> next;
		free(prevNode);
	}
}

void *ThreadTask(void *threadNodeArg){
	ThreadNode *myNode = (ThreadNode*)threadNodeArg;
		
	//perform computation, signal is successful
	SchemaEnt** resultPointer = myNode->resultPointer;
	SchemaEnt* schema;// = myNode->schema;
	int* wait = myNode->wait;
	//char* schemaName;// = schema->name;
	char* desiredToken = myNode->desiredToken;
	pthread_mutex_t *lock = myNode->lock;
	Queue *queue = myNode->queue;
	//pthread_cond_t *condition = myNode->condition;
	int i;
	//for(i = 0; *wait && i<myNode->comparisons; i++){
	while(*wait){
		//printf("counter: %d and flag: %d and system id: %d before deq\n", *myNode->counter, *wait, myNode->thread_id);
		QueueItem *item = deq(queue, wait, myNode->thread_id, myNode->comparisons);
		QueueItem *prev;
		while(item){
			schema = item->schema;
			//free(item);
			if(*wait && strcmp(schema->name, desiredToken) == 0){
				//use lock here
				pthread_mutex_lock(lock);
				//printf("found %s at %p\n", desiredToken, schema);
				*resultPointer = schema;
				*wait = 0;
				//pthread_cond_signal(condition);
				wake_queue(queue);
				pthread_mutex_unlock(lock);
		
		
				pthread_mutex_lock(myNode->counterLock);
				(*myNode->counter)--;
				//printf("(inside while)counter: %d and flag: %d and system id: %d\n", *myNode->counter, *wait, myNode->thread_id);
				pthread_cond_broadcast(myNode->counterCond);
				pthread_mutex_unlock(myNode->counterLock);
		
		
				break;
			}
			pthread_mutex_lock(myNode->counterLock);
			(*myNode->counter)--;
			//printf("counter: %d and flag: %d and system id: %d\n", *myNode->counter, *wait, myNode->thread_id);
			pthread_cond_broadcast(myNode->counterCond);
			pthread_mutex_unlock(myNode->counterLock);
			
			prev = item;
			item = item->next;
			free(prev);
			//schema = schema->next;
		} /*
		if(!item){
			break;
		}*/
	}
	//free(threadNodeArg);
	pthread_exit(NULL);
}