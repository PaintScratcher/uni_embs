// Exam number: Y0070813
package code;

import java.util.Arrays;
import java.util.Random;

public class Question2 {
	// Class that can be used to calculate a mapping for tasks onto processing elements for a given Network on Chip and a set of tasks.
	// Uses simulated annealing with a heuristic based on the total Manhattan distance between a tasks PE and it's communication destination PE and PE utilization
	
	// Variables to be set, configures the network topography and task information
	private static final int nocDimensions = 4; // X,Y size of the NoC, must by symmetrical 
	private static final int numberOfTasks = 20; // The total number of tasks
	// For all the arrays, the index of the array is the task number, ie task 2 is stored in Array[2] (as tasks are 0 indexed)
	private static int[] taskPriorities = {8,9,10,11,12,13,14,2,3,15,4,5,6,7,0,1,16,17,18,19}; // Priorities of the tasks
	private static int[] taskDestinations = {5,5,5,5,5,6,9,8,9,10,5,14,14,14,15,14,17,18,19,16}; // Communication destination of the tasks (stored as TaskID)
	private static double[] taskPeriods = {6.4,6.4,6.4,1,1,3,8.2,3.5,3.5,7,3.2,3.2,3.2,3.2,1.5,1.5,10,10,10,10}; // Periods of the tasks
	private static double[] taskCompTimes = {0.8,0.8,0.8,0.8,0.8,1,1,0.5,0.4,1.1,0.8,1.2,0.4,0.4,0.9,0.5,5.5,5.5,5.5,5.5}; // Computation time of the tasks
	private int [][] initialMap = {{0,1},{0,2},{2,0},{1,0},{1,3},{0,3},{2,1},{3,0},{1,2},{3,1},{0,0},{3,0},{1,1},{1,2},{0,0},{2,3},{2,2},{0,1},{3,2},{3,3}}; // Initial mapping, from which to start the annealing. Currently M1
	
	private int iterations = 0; // Number of iterations the annealing has generated
	
	
	public static void main(String[] args) {
		Question2 App = new Question2();
		// Sanity check to ensure all input arrays are valid
		if (taskDestinations.length != numberOfTasks || taskPeriods.length != numberOfTasks || taskCompTimes.length != numberOfTasks || taskPriorities.length != numberOfTasks){
			System.out.println(taskDestinations.length + taskPeriods.length + taskCompTimes.length + taskPriorities.length);
		}
		
		for (int[] task : App.start()){ // Generate a mapping, then iterate through the mapping and print it out
			System.out.println(Arrays.toString(task));
		}
	}
	
	private int [][] start(){
		// Main annealing algorithm - based on implementations found at http://www.theprojectspot.com/tutorial-post/simulated-annealing-algorithm-for-beginners/6
		// and http://katrinaeg.com/simulated-annealing.html
		// Simulated Annealing uses a cost function to compare a randomly mutated mapping with the current mapping and uses an acceptance function to decide
		// if the new mapping should be used. The temperature and cooling mechanism ensures a minimal value is found by avoiding local minimums by making
		// worse cost mappings more likely to be chosen initially and then gradually reducing the likelihood of acceptance of a new positive cost mapping
		int [][] newMap = new int[numberOfTasks][2]; // Initialize a 2 dimensional array to store the generated mapping
		double currentCost = cost(initialMap); // Calculate the cost of the initial mapping 
		int[][] currentMap = initialMap; // Set the current map to the initial map
		int[][] bestMap = initialMap; // Set the current best map to the initial map
		double bestCost = currentCost; // Set the current best cost to the current cost
		double temperature = 1.0; // Annealing starting temperature
		double coolingRate = 0.9; // Annealing cooling rate

		while(temperature > 0.00001){ // End condition for the annealing and main loop
			for(int i = 0; i<100;i++){ // For each temperature, try 100 different mappings to find the best one at that temperature (Avoids local minima)
				iterations++; // Increment the iterations number
				System.out.println(); // Debug print
				newMap = createNewMap(currentMap); // Create a new map
				double newCost = cost(newMap); // Calculate the cost of a new map
				if(Math.exp((currentCost - newCost)/temperature) > Math.random()){ // Acceptance function
					currentMap = newMap; // If we accept the new map, set the currentMap to the newMap
					currentCost = newCost; // Set the current cost to the new cost
				}
				
				if(newCost < bestCost){ // Store the overall best mapping found
					bestMap = currentMap;
					bestCost = newCost;
				}
			}
			temperature *= coolingRate; // Cool the temperature, to adjust the acceptance function
		}
		System.out.println("----------------------------"); // Print out the statistics associated with the best mapping at the end of the run
		cost(bestMap);
		System.out.println("Iterations: " + iterations);
		return bestMap;
	}

	private int[][] createNewMap(int [][]currentMap){
		//Method that randomly mutates the current mapping to generate a new map
		Random random = new Random(); // Initialise a random number generator
		int [][] newMap = new int[numberOfTasks][2]; // Initialise a 2 dimensional array to store the new map
		
		for (int t = 0; t < numberOfTasks; t++) { // Clone the current mapping to the new map
			newMap[t][0] = currentMap[t][0];
			newMap[t][1] = currentMap[t][1];
		}
		
		int task = random.nextInt(numberOfTasks); // Choose a random task
		
		newMap[task][0] = random.nextInt(4); // Assign the task to a random processing element
		newMap[task][1] = random.nextInt(4);
		return newMap;
	}
	
	private double cost(int [][] map){
		//Method to calculate the cost of a mapping. Cost is based on the total Manhattan distance between a tasks PE and it's communication destination PE and PE utilization
		double totalDistance = 0; // Initiaise a variable to store the total distance
		
		for (int i = 0; i < map.length; i++) { // For each task in the mapping
			int [] routerLocation = map[i]; // Get the location of the router as an [x,y] array
			int destination = taskDestinations[i]; // Get the tasks communication destination
			int [] destintaionLocation = map[destination];// Get the location of the destination router as an [x,y] array
			totalDistance += Math.abs(routerLocation[0]-destintaionLocation[0]) + Math.abs(routerLocation[1]-destintaionLocation[1]); // Get the Manhattan distance between the routers and add it to the total distance 
		}
		double processorLoad = processorLoad(map); // Get the processor load for the given mapping
		System.out.println("Distance " + totalDistance + " PL " + processorLoad); // Print the cost statistics
		double cost = (totalDistance/60.0) + (200*processorLoad); // Calculate the cost factor. Distance is divided by 60, as this is the distance for the M1 mapping and brings the distance to low integer. Processor load is timed by 200 to ensure it can have a large effect on the overall cost.
		System.out.println("COST: "+cost);
		return cost;
	}
	
	private double processorLoad(int[][] map){
		// Method to find the processor load for a mapping. Utilization is calculated as the total utilisation for each task on a PE
		// the utilisation of a task is its computation time divided by its period. If the utilisation is over 0.7, the PE is counted as being overloaded
		// and the amount it is overloaded is calculated and returning for use in the cost function
		double overload = 0.0; // Initialise the total amount of overload for the NoC
		for (int y = 0; y < 4; y++) { // We need to iterate through each PE, so for both x and y.
			for (int x = 0; x < 4; x++) {
				double utilisation = 0.0; // Initialise a variable to store the PE's utilisation
				for (int t = 0; t < numberOfTasks; t++) { // Iterate through each task
					if (map[t][0] == x && map[t][1] == y) { // If the task is mapped to the current PE
						utilisation += taskCompTimes[t] / taskPeriods[t]; // Calculate the utilisation for the current task and add it to the total for the PE
					}
				}
				System.out.println(utilisation);
				if(utilisation > 0.7){ // Determine if the current PE is overloaded
					overload += utilisation - 0.7; // Calculate the amount by which the PE is overloaded and add it to the total
				}
			}
		}
		return overload;
	}
}
