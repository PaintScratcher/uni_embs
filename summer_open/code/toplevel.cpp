#include "toplevel.h"

// Data storage
uint8 distanceMatrix[MAX_NUMBER_OF_WAYPOINTS][MAX_NUMBER_OF_WAYPOINTS]; // Structure to store the distances between waypoints
uint6 numberOfWalls; // Number of walls in the world being solved
uint6 numberOfWaypoints; // Number of waypoints in the world being solved
uint6 worldSize; // The size of the world being solved
uint32 receiveBuffer; // Buffer for the input AXI stream from the MicroBlaze
Node storageGrid[MAX_WORLD_SIZE][MAX_WORLD_SIZE]; // Structure to store the world node information for the A* algorithm
Coordinate waypoints[MAX_NUMBER_OF_WAYPOINTS]; // Structure to store information on the waypoints in the world being solved

// Function prototypes
void checkAndUpdateNode(uint8 X, uint8 Y, uint12 cost, Direction parentDirection);
uint12 aStarSearch(Coordinate startWaypoint, Coordinate destinationWaypoint);

uint12 manhattanDistance(uint8 X1, uint8 Y1, uint8 X2, uint8 Y2){
	// Takes the X and Y coordinates of two grid positions in the world and returns
	// the Manhattan distance between them
	return (abs(X1 - X2) + abs(Y1 - Y2));
}

void toplevel(hls::stream<uint32> &input, hls::stream<uint32> &output) {
	// Main function of the hardware segment of the code

	// Define the AXI streams to communicate with MicroBlaze
	#pragma HLS RESOURCE variable=input core=AXI4Stream
	#pragma HLS RESOURCE variable=output core=AXI4Stream
	#pragma HLS INTERFACE ap_ctrl_none port=return

	// ***Read in the data from the MicroBlaze via an AXI stream***
	worldSize = input.read(); // Read the grid size of the current world being solved

	numberOfWaypoints = input.read(); // Read the number of waypoints in the world
	waypointReadLoop: for(int i = 0; i < numberOfWaypoints; i++) {
		// For each waypoint in the world, read its information from the input stream and store it for use later
		receiveBuffer = input.read();
		waypoints[i].X = (uint8) (receiveBuffer >> 8); // Input is two bytes wide so we right shift by 1 byte to store the first one
		waypoints[i].Y = (uint8) receiveBuffer;
	}

	numberOfWalls = input.read(); // Read the number of walls in the world
	Wall wall; // Create a wall object to store the received wall
	wallReadLoop:	for(int wallLoopCount = 0; wallLoopCount < numberOfWalls; wallLoopCount++) {
		// For each wall in the world, read its information from the input stream and store it for later use
		receiveBuffer = input.read();
		wall.X = (uint8) (receiveBuffer >> 24); // Input is four bytes wide so we right shift by 1 byte more each time to store the correct bytes
		wall.Y = (uint8) (receiveBuffer >> 16);
		wall.direction = (uint8) (receiveBuffer >> 8);
		wall.length = (uint8) receiveBuffer;

		if(wall.direction == 0){ // If the wall is horizontal
			for(int i = 0; i < wall.length; i++){ // Store its position in the world grid for its length in the X direction
				storageGrid[wall.X + i][wall.Y].isWall = 1;
			}
		}
		else{ // If the wall is vertical
			for(int i = 0; i < wall.length; i++){ // Store its position in the world grid for its length in the Y direction
				storageGrid[wall.X][wall.Y + i].isWall = 1;
			}
		}
	}

	// *** Perform A* algorithm to populate the distance matrix ***
	numberOfWaypointsLoop: for(int waypoint = 0; waypoint < numberOfWaypoints; waypoint++) {
		// Loop through the waypoints and populate the distance matrix by performing A* search
		destinationsLoop: for(int destinationWaypoint = 0; destinationWaypoint < numberOfWaypoints; destinationWaypoint++){
			// Loop through all other waypoints as destinations to ensure the distance matrix is populated
			if(waypoint == destinationWaypoint)continue; // Do not find the distance to the same position
			if(distanceMatrix[waypoint][destinationWaypoint] != 0)continue; // Distance matrix is symmetric, so dont calculate distance if we already have one

			// Find the best route distance between waypoints using A* and store the result
			uint12 aStarResult = aStarSearch(waypoints[waypoint],waypoints[destinationWaypoint]);
			distanceMatrix[waypoint][destinationWaypoint] = aStarResult; // Distance matrix is symmetric, so store in both combinations
			distanceMatrix[destinationWaypoint][waypoint] = aStarResult;
		}
	}

	// *** Permutate through all the possibilities of waypoints to find the cheapest route ***

	uint12 lowestCost = 3600; // Cost of the lowest cost route found
	uint6 bestRoute[MAX_NUMBER_OF_WAYPOINTS + 1]; // Array to store the best route found


	// Permutation algorithm adapted from http://www.quickperm.org/
	// [P.  Fuchs, "Permutation Algorithms Using Iteration and the Base-N-Odometer Model (Without Recursion)", Quickperm.org, 2016. [Online]. Available: http://www.quickperm.org/. [Accessed: 01- May- 2016]]

	uint6 waypointsToPermute[MAX_NUMBER_OF_WAYPOINTS - 1]; // Array to store the waypoints to permute
	int N = numberOfWaypoints -1; // Controls the number of permutations, 1 less than the number of waypoints as we do not permute the starting ndoe (it is static in the route)
	uint6 p[MAX_NUMBER_OF_WAYPOINTS]; // Array that controls the permutations
	for(int i = 0; i < N; i++){ // Populate the control arrays
		waypointsToPermute[i] = i + 1;
		p[i] = i;
	}
	p[N] = N;
	int i = 1;
	int j, temp;
	uint12 currentRouteCost;
	while(i < N){
		p[i]--; // Reduce the number of iterations required for this position
		j = i % 2 * p[i]; // Calculate the positions to be swapped

		// Swap the waypoints store at two positions
		temp = waypointsToPermute[j];
		waypointsToPermute[j] = waypointsToPermute[i];
		waypointsToPermute[i] = temp;

		// Calculate the cost of the route in the current permutation
		currentRouteCost = distanceMatrix[0][waypointsToPermute[0]]; // Cost between the starting node and the first node in the permutation
		for(int x = 0; x < numberOfWaypoints -2; x++){ // Cost between the nodes in the permutation
			currentRouteCost += distanceMatrix[waypointsToPermute[x]][waypointsToPermute[x+1]];
		}
		currentRouteCost += distanceMatrix[waypointsToPermute[numberOfWaypoints -2]][0]; // Cost between the final node in the permutation back to the start node

		// Determine if the cost of the current route permutation is less than the global minimum
		if(currentRouteCost < lowestCost){
			// Cost of the current route is the current global minimum so we store this route for further comparison
			lowestCost = currentRouteCost; // Update the current lowest cost found
			bestRoute[0] = 0; // Set the first node in the route to be the start node
			for(int x = 0; x < numberOfWaypoints; x++){ // Update the best route with the positions in this permutation
				bestRoute[x + 1] = waypointsToPermute[x];
			}
			bestRoute[numberOfWaypoints] = 0; // Set the last node in the route to be the start node to complete the cycle
		}
		i = 1;
		while(!p[i]){ // Update the iteration counter to ensure proper number of permutations are generated
			p[i] = i;
			i++;
		}
	}

	// *** Now we have the best route round the waypoints cycle, we need to generate the steps on the route and send them back to the Microblaze ***
	Coordinate position;
	int tempPosition;

	output.write(lowestCost); // Send the cost of the route to the Microblaze so the software knows how many points on the route are to be received
	for(int x = 0; x < numberOfWaypoints; x++){
		// For every pair of waypoints next too each other in the best route we need to re-run A* to find the best route between them so we can send that to software
		aStarSearch(waypoints[bestRoute[x]],waypoints[bestRoute[x+1]]); // Run A* between the two waypoints to update storageGrid with node parents
		// A* starts at the destination waypoint and returns to the start waypoint, storing the parent nodes as it goes
		// We can re-trace the route by following the parent routing through the grid until we find the start waypoint
		position.X = waypoints[bestRoute[x+1]].X; // X coordinate of our current position
		position.Y = waypoints[bestRoute[x+1]].Y; // Y coordinate of our current position
		while(position.X != waypoints[bestRoute[x]].X || position.Y != waypoints[bestRoute[x]].Y){
			// While we are not at the start waypoint
			switch(storageGrid[position.X][position.Y].parentDirection){
			// Read the direction of the current nodes parent and update our current position accordingly
			case NORTH:
				position.Y -= 1;
				break;
			case EAST:
				position.X += 1;
				break;
			case WEST:
				position.X -= 1;
				break;
			case SOUTH:
				position.Y += 1;
				break;
			}

			tempPosition = position.X;
			tempPosition = tempPosition << 8 | position.Y; // Construct our position in a 32bit int for output on the AXI stream
			output.write(tempPosition); // Output our current position to the Microblaze such that it can be printed to the VGA
		}
	}
}

uint12 aStarSearch(Coordinate startWaypoint, Coordinate destinationWaypoint){
	// Reset the costs and list memberships for all the nodes in the world
	for(int i =0; i < 60; i++){
		for(int j =0; j < 60; j++){
			storageGrid[i][j].cost = 0;
			storageGrid[i][j].listMembership = 0;
		}
	}
	storageGrid[startWaypoint.X][startWaypoint.Y].listMembership = 1;

	bool openListEmpty = 0;
	mainAStarLoop: while(openListEmpty == 0){
		uint12 lowestCost = 3600;
		Coordinate position;
		openListEmpty = 1;
		for(int x = 0; x < worldSize; x++){
			for(int y = 0; y < worldSize; y++){
				if(storageGrid[x][y].listMembership == 1){
					openListEmpty = 0;
					uint12 currentNodeCost = storageGrid[x][y].cost + manhattanDistance(x, y ,destinationWaypoint.X, destinationWaypoint.Y);
					if(currentNodeCost < lowestCost){
						position.X = x;
						position.Y = y;
						lowestCost = currentNodeCost;
					}
				}
			}
		}
		storageGrid[position.X][position.Y].listMembership = 2;
		if(position.X == destinationWaypoint.X && position.Y == destinationWaypoint.Y){
			return lowestCost;
		}
		checkAndUpdateNode(position.X, position.Y +1, storageGrid[position.X][position.Y].cost, NORTH); // SOUTH
		checkAndUpdateNode(position.X + 1, position.Y, storageGrid[position.X][position.Y].cost, WEST); // EAST
		checkAndUpdateNode(position.X - 1, position.Y, storageGrid[position.X][position.Y].cost, EAST); // WEST
		checkAndUpdateNode(position.X, position.Y -1, storageGrid[position.X][position.Y].cost, SOUTH); // NORTH
	}
}

void checkAndUpdateNode(uint8 X, uint8 Y, uint12 cost, Direction parentDirection){
	if(!storageGrid[X][Y].isWall && X >= 0 && Y>=0 && X < worldSize && Y < worldSize){
		if(storageGrid[X][Y].listMembership != 2){
			if(storageGrid[X][Y].listMembership != 1){
				storageGrid[X][Y].listMembership = 1;
				storageGrid[X][Y].cost = cost + 1;
				storageGrid[X][Y].parentDirection = parentDirection;
			}
			else if(storageGrid[X][Y].cost > cost + 1){
				storageGrid[X][Y].cost = cost + 1;
				storageGrid[X][Y].parentDirection = parentDirection;
			}
		}
	}
}

