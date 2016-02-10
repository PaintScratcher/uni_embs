// Exam number: Y0070813
package code;

import java.util.ArrayList;
import java.util.Collections;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class question1 extends TypedAtomicActor {
	// Actor class to calculate various statistics about tasks running on a NOC
	 protected TypedIOPort input; // Input port, where task records are received
	 protected TypedIOPort output; // Output port, used to connect a display to print information if needed
	 private ArrayList<Task> taskStore = new ArrayList<Task>(Collections.nCopies(20, null)); // Structure to store the task objects
	 private final int N = 19; // Maximum task id
	 private double average; // Store the average for use across the class

	 public question1(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException  {
			super(container, name);
			input = new TypedIOPort(this, "input", true, false);  // Input port, where task records are received
			output = new TypedIOPort(this, "output", false, true); // Output port, used to connect a display to print information if needed
			
	}
	 
	public void initialize() throws IllegalActionException {
		// Initialise method,  run when the simulation is started
		getDirector().fireAt(this, 0.5); // Set the actor to fire at 0.5s to generate stats at the end of the simulation
	}
	 
	public void fire() throws IllegalActionException{
		// Fire method, which is run every time a token is received on the input port or when the actor is fired via the fireAt method
		if (getDirector().getModelTime().getDoubleValue() == 0.5){ // If the time is 0.5s, so the end of the simulation
			System.out.println("ID,Max Comm,UQ Comm,Av Comm,LQ Comm,Min Comm,Max E2E,UQ E2E,Av E2E,LQ E2E,MIN E2E,Deadline Fails"); // Print column headings
			double quality = 0; // Initialse the quality variable
			for(Task task : taskStore){ // We need to iterate through every task that has been run on the NOC to generate its stats
				System.out.print(task.taskID + ","); // Print out the task id, for the row
			    printStats(task.commtimeStore); // Generate and print the tasks communication time stats
			    printStats(task.endToEndStore); // Generate and print the tasks end to end time stats
			    System.out.print(","+task.numberOfDeadlineMisses+"\n");
			    quality += (N - task.priority) * ((task.period - average) - task.numberOfDeadlineMisses); // Calculate the quality metric and add it to the total
			}
			System.out.println("Quality Metric: " + quality); // Print the final quality metric value
		}
		else{ // The time is not 0.5s, so it is not a scheduled firing and so we must have a token on the input
			RecordToken taskToken = (RecordToken)input.get(0); // Get the token on the input
			int id = ((IntToken) taskToken.get("id")).intValue(); // Get the task ID from the token
			int priority = ((IntToken) taskToken.get("priority")).intValue(); // Get the priority value from the token
			double compfinishtime = ((DoubleToken) taskToken.get("compfinishtime")).doubleValue(); // Get the computation finish time from the token
			double commfinishtime = ((DoubleToken) taskToken.get("commfinishtime")).doubleValue(); // Get the communication finish time from the token
			double period = ((DoubleToken) taskToken.get("period")).doubleValue(); // Get the task's period value from the token
			double releasetime = ((DoubleToken) taskToken.get("releasetime")).doubleValue(); // Get the release time from the token
			
			if (taskStore.get(id) == null){ // If we do not have this task stored in taskStore 
				Task newTask = new Task(); // Generate an object that we can use to store the information for this task
				newTask.taskID = id; // Store the information from the token to the new object
				newTask.priority = priority;
				newTask.period = period;
				taskStore.set(id, newTask); // Insert the created object to the taskStore structure
			}
			Task task = taskStore.get(id); // Get the stored information for the current task
			
			task.commtimeStore.add(commfinishtime - compfinishtime); // Calculate and store the communication latency for the task
			task.endToEndStore.add(commfinishtime - releasetime); // Calculate and store the end to end latency for the task
	
			if (releasetime + period < commfinishtime){ // If the task has missed its deadline (took longer to complete than its period)
				task.numberOfDeadlineMisses++; // Increment the number of deadline misses, which is needed to calculate the quality metric
			}
			output.send(0, taskToken); // Send the token to the output port, so it can be displayed
		}
	 }
	private double[] findStats(ArrayList<Double> list){
		// Method for calculating various statistics about latencies
		double[] returnValue = new double[3]; // Initialize an array that we can return to the calling method
		int middle = list.size()/2; // Find the middle value of the array
		double lowerQuartile; // Variables to store the calculated values
		double median;
		double upperQuartile;
		average = findMeanAverage(list); // Find the mean average of the data
		// Calculating the upper and lower quartiles: 
		if (list.size()%2 == 1){ // If the size of the list is odd
			lowerQuartile = list.get(middle/2); // Using integer division we can get the lower quartile by dividing the middle index by 2
			median = list.get(middle); // Median is simply the middle value
			upperQuartile = list.get(middle + (middle/2)); // By adding the middle value integer divided by 2 to the middle value we have the upper quartile
		}
		else{ // If the size of the list is even
			int quart = list.size() / 4; 
			lowerQuartile = list.get(quart); // Quartiles are the size/4'th value for even size's
			median = (list.get(middle-1) + list.get(middle)) / 2; // We need to find the mean average between the two middle values
			upperQuartile = list.get(list.size()-1-quart);
		}
		returnValue[0] = lowerQuartile; // Populate the return array
		returnValue[1] = average;
		returnValue[2] = upperQuartile;
		return returnValue;
	}
	private double findMeanAverage(ArrayList<Double> list){
		// Method to find the mean average of a list
		double sum = 0;
		for(double elem : list){
	    	sum += elem;
	    }
		return sum / list.size();
	}
	
	private void printStats(ArrayList<Double> list){
		// Print the statistics for each id
		Collections.sort(list); // Sort the array of latencies for use in finding quartiles
		double Min = list.get(0); // Minimum Value
	    double Max = list.get(list.size() -1); // Maximum value
	    double Lower = findStats(list)[0]; // As findstats returns an array, we can get all the stats with one function
	    double Average = findStats(list)[1];
	    double Upper = findStats(list)[2];
	    
	    System.out.print(Max+ "," + Upper+","+Average+","+Lower+","+Min+","); // Print a row with all the stats for one task
	}

	public class Task{
		// Class to store information about an individual task
		ArrayList<Double> commtimeStore = new ArrayList<Double>(); // Store the communication latencies 
		ArrayList<Double> endToEndStore = new ArrayList<Double>(); // Store the end to end latencies 
		ArrayList<Double> slackStore = new ArrayList<Double>(); // Store the slack values, for use in the quality metric
		int numberOfDeadlineMisses = 0; // Store the number of deadline misses for this task
		int taskID; // The Task's TaskID
		int priority; // The Task's priority
		double period; // The period of the task
	}
}

