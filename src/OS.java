//Patson Emmanuel Dsouza | H00404212 | ped2001
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition; //Note that the 'notifyAll' method or similar polling mechanism MUST not be used

// IMPORTANT:
//
//'Thread safe' and 'synchronized' classes (e.g. those in java.util.concurrent) other than the two imported above MUST not be used.
//
//You MUST not use the keyword 'synchronized', or any other `thread safe` classes or mechanisms  
//or any delays or 'busy waiting' (spin lock) methods. Furthermore, you must not use any delays such as Thread.sleep().

//However, you may import non-tread safe classes e.g.:
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;


//Your OS class must handle exceptions locally i.e. it must not explicitly 'throw' exceptions 
//otherwise the compilation with the Test classes will fail!!!


//this holds information about all processes that are created
class Process {
    private int pid;
    private int priority;
    private boolean isStarted; // Tracker to check if the process has started

    public Process(int pid, int priority) {
        this.pid = pid;
        this.priority = priority;
        this.isStarted = false; // Initialize isStarted to false
    }

    public int getPid() {
        return pid;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public void setStarted(boolean started) {
        isStarted = started;
    }
}



public class OS implements OS_sim_interface {
	//this is to keep track of the number of processors available
	private int number_of_processors = 0;
	
	//this is to keep track of the number of processes registered
	private int number_of_processes = 0;
	
	// all created process go into this ArrayList
	private ArrayList<Process> allProcesses = new ArrayList<>();
	
	// Define ReentrantLock
    private final ReentrantLock lock = new ReentrantLock();
	
    // Define Condition for waiting threads in start
    private final Condition processRestart = lock.newCondition();

    //map that holds a queue for every priority
    private Map<Integer, Queue<Process>> priorityQueues = new HashMap<>();

	
    

	
	
	@Override
	public void set_number_of_processors(int nProcessors) {
		
		number_of_processors = nProcessors;
		
	}

	@Override
	public int reg(int priority) {
		lock.lock();
		try {
			Process newProcess = new Process(number_of_processes, priority);
			allProcesses.add(newProcess);
			number_of_processes +=1 ;
			return newProcess.getPid();
		}finally {
			lock.unlock();
		}
	}

	@Override
	public void start(int ID) {
	    Process processToStart = null;
	    lock.lock();
	    try {
	        // Find the process with the given PID
	        for (Process process : allProcesses) {
	            if (process.getPid() == ID) {
	                processToStart = process;
//	                System.out.println("\nProcess with PID " + ID + " has been found and is ready to start.");
	                break;
	            }
	        }
	        
	        if (processToStart != null) { // When the process is registered
	            // If no processors available, suspend the caller thread
	            if (number_of_processors <= 0) {
//	                System.out.println("Process with PID " + ID + " didn't get a processor free and is now waiting on ready queue");
	                addToPriorityQueue(processToStart);
	                
//	                System.out.println("Process with PID " + ID + " is now waiting out of  loop.");
	                processRestart.await();
	                while (processToStart != getHighestPriorityProcess()) {
	                	//signalling other waiting threads that this process is giving up control as it is not the top process
//	                	System.out.println("Process with PID " + ID + " is not it, signal sent back");
	                	processRestart.signal();
//	                	System.out.println("Process with PID " + ID + " is now waiting in loop.");
	                    processRestart.await();
	                }
	                //readyProcesses.remove();
	                removeFromPriorityQueue(processToStart);
//	                System.out.println("Process with PID " + ID + " didn't get a processor free and is now started");
	                processToStart.setStarted(true);
	                number_of_processors--;
	                
	            } else {
	                //when there was a processor available 
	                processToStart.setStarted(true);
	                // Execute the process here
//	                System.out.println("Process with PID " + ID + " started.");
	                // Decrement the number of processors
	                number_of_processors--;
	            }
	        } else {
//	            System.out.println("Process with PID " + ID + " has not been found.");
	        }
	        
	    } catch (InterruptedException e) {
	        e.printStackTrace();
	        Thread.currentThread().interrupt();
	    } finally {
	        lock.unlock();
	    }
	}
	
	@Override
	public void schedule(int ID) {
	    Process processToSchedule = null;
	    lock.lock();
	    try {
	        // Find the process with the given PID
	        for (Process process : allProcesses) {
	            if (process.getPid() == ID) {
	                processToSchedule = process;
	                break;
	            }
	        }
	        // starting processes and catching processes that haven't started but called schedule as they were suspended and returned to caller
	        if(!processToSchedule.isStarted()) {
	        	//forcing await
//	        	System.out.println("Process with PID " + ID + " is now waiting.");
	        	processRestart.await();
	        	processToSchedule.setStarted(true);
//	        	System.out.println("\nProcess with PID " + ID + " has started from the ready Queue");
	        	removeFromPriorityQueue(processToSchedule);
	        }
	        
	        //processes that can successfully give up control
	        addToPriorityQueue(processToSchedule);
//	        System.out.println("Process with PID " + ID + " is ready to schedule and will give control to: PID " + getHighestPriorityProcess().getPid() +  ".");
	        
	        if (processToSchedule != null) { // When the process is registered

	            // giving up processor
	            number_of_processors++;
	            
	            // look at queue 
	            //System.out.println("Ready processes: " + readyProcessesToString());

	            // Check if there are any processes ready to start
	            if (!priorityQueues.isEmpty()) {
	                // Determine which process should get control (the one at the top of the queue)
	                //Process processToGiveControl = readyProcesses.peek();
	                Process processToGiveControl = getHighestPriorityProcess();
	                
	                // If the current process is not the one that should get control, await until it is
	                while (processToSchedule != processToGiveControl) {
	                	//signalling other waiting threads that this process is giving up control as it is not the top process
//	                	System.out.println("Process with PID " + ID + " has sent a signal.");
	                	processRestart.signal();
	                	
//	                	System.out.println("Process with PID " + ID + " has is now waiting.");
	                    processRestart.await();
	                    processToGiveControl = getHighestPriorityProcess(); // Update processToGiveControl after await
	                }
	                //when we regain control we want to remove it from queue
	                removeFromPriorityQueue(processToGiveControl);
	                
	                //given back control when it was on top
	                //readyProcesses.remove();
	                
	                //message indicating that the process has regained control
//	                System.out.println("\nProcess with PID " + ID + " has regained control.");
	                
	                // Decrement the number of processors
	                number_of_processors--;
	            } else {
//	                System.out.println("No processes ready to start.");
	            }	            
	        }
	    } catch (InterruptedException e) {
	        e.printStackTrace();
	        Thread.currentThread().interrupt();
	    } finally {
	        lock.unlock();
	    }
	}

	private void addToPriorityQueue(Process process) { //adding process to our map of queues, creating key if priorty not already present
        int priority = process.getPriority();
        priorityQueues.putIfAbsent(priority, new LinkedList<>());
        priorityQueues.get(priority).add(process);
    }

    private void removeFromPriorityQueue(Process process) { //removing the process from the map of queues
        int priority = process.getPriority();
        Queue<Process> queue = priorityQueues.get(priority);
        if (queue != null) {
            queue.remove(process);
        }
    }

    private Process getHighestPriorityProcess() { //gets highest priority process within the map of queues
        int highestPriority = Integer.MAX_VALUE;
        Process highestPriorityProcess = null;
        for (Map.Entry<Integer, Queue<Process>> entry : priorityQueues.entrySet()) {
            int priority = entry.getKey();
            Queue<Process> queue = entry.getValue();          
            if (!queue.isEmpty() && priority < highestPriority) {
                highestPriority = priority;
                highestPriorityProcess = queue.peek();
            }
        }
        return highestPriorityProcess;
    }
	

	@Override
	public void terminate(int ID) {
		lock.lock();
	    try {
	        Process processToRemove = null;
	        for (Process process : allProcesses) {
	            if (process.getPid() == ID) {
	                processToRemove = process;
	                break;
	            }
	        }
	        
	        if (processToRemove != null) {
	        	
	            // Remove the process from the allProcesses list
	            allProcesses.remove(processToRemove);
	            // Remove the process from the readyProcesses list if it's present
	            removeFromPriorityQueue(processToRemove);
	            
//	            // freeing and making processors available
	            number_of_processors++;
	            
//	            //give control to awaiting process
	            if(getHighestPriorityProcess() != null) {
//	            System.out.println("Process with PID " + ID + " has sent a signal while terminating.");
	            processRestart.signal();}
	            
//	            System.out.println("\nProcess with PID " + ID + " terminated.");
//	            System.out.println("\next process will be." + getHighestPriorityProcess().getPid());
	        } else {
//	            System.out.println("Process with PID " + ID + " not found.");
	        }
	    } finally {
	        lock.unlock();
	    }
	}
}
