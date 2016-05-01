#include "toplevel.h"

// Data storage
uint8 distanceMatrix[MAX_NUMBER_OF_WAYPOINTS][MAX_NUMBER_OF_WAYPOINTS]; // Structure to store the distances between waypoints
uint6 numberOfWalls; // Number of walls in the world being solved
uint6 numberOfWaypoints; // Number of waypoints in the world being solved
uint6 worldSize; // The size of the world being solved
uint32 receiveBuffer; // Buffer for the input AXI stream from the MicroBlaze
Node storageGrid[MAX_WORLD_SIZE][MAX_WORLD_SIZE]; // Structure to store the world node information for the A* algorithm
uint8 waypoints[MAX_NUMBER_OF_WAYPOINTS][2]; // Structure to store information on the waypoints in the world being solved

// Function prototypes
void checkAndUpdateNode(uint8 X, uint8 Y, uint12 cost, Direction parentDirection);
uint12 aStarSearch(uint8 startX, uint8 startY, uint8 destX, uint8 destY);
void findBestRoute();

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
		waypoints[i][0] = (uint8) (receiveBuffer >> 8); // Input is two bytes wide so we right shift by 1 byte to store the first one
		waypoints[i][1] = (uint8) receiveBuffer;
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
			uint12 aStarResult = aStarSearch(waypoints[waypoint][0], waypoints[waypoint][1], waypoints[destinationWaypoint][0], waypoints[destinationWaypoint][1]);
			distanceMatrix[waypoint][destinationWaypoint] = aStarResult; // Distance matrix is symmetric, so store in both combinations
			distanceMatrix[destinationWaypoint][waypoint] = aStarResult;
		}
	}

	// *** Permutate through all the possibilities of waypoints to find the cheapest route ***

	uint12 lowestCost = 3600;
	uint6 bestRoute[MAX_NUMBER_OF_WAYPOINTS + 1];
	uint6 waypointsToPermute[MAX_NUMBER_OF_WAYPOINTS - 1];

	int N = numberOfWaypoints -1;
	uint6 p[MAX_NUMBER_OF_WAYPOINTS];
	for(int i = 0; i < N; i++){
		waypointsToPermute[i] = i + 1;
		p[i] = i;
	}
	p[N] = N;
	int i = 1;
	int j, temp;
	uint12 currentRouteCost;
	while(i < N){
		p[i]--;
		j = i % 2 * p[i];
		temp = waypointsToPermute[j];
		waypointsToPermute[j] = waypointsToPermute[i];
		waypointsToPermute[i] = temp;
		//Do the thing here
		currentRouteCost = distanceMatrix[0][waypointsToPermute[0]];
		for(int x = 0; x < numberOfWaypoints -2; x++){
			currentRouteCost += distanceMatrix[waypointsToPermute[x]][waypointsToPermute[x+1]];
		}
		currentRouteCost += distanceMatrix[waypointsToPermute[numberOfWaypoints -2]][0];
		if(currentRouteCost < lowestCost){
			lowestCost = currentRouteCost;
			bestRoute[0] = 0;
			for(int x = 0; x < numberOfWaypoints; x++){
				bestRoute[x + 1] = waypointsToPermute[x];
			}
			bestRoute[numberOfWaypoints] = 0;
		}
		i = 1;
		while(!p[i]){
			p[i] = i;
			i++;
		}
	}
	uint8 position[2],tempPosition[2];
	int test;
	output.write(lowestCost);
	for(int x = 0; x < numberOfWaypoints; x++){
		aStarSearch(waypoints[bestRoute[x]][0], waypoints[bestRoute[x]][1], waypoints[bestRoute[x+1]][0], waypoints[bestRoute[x+1]][1]);
		position[0] = waypoints[bestRoute[x+1]][0];
		position[1] = waypoints[bestRoute[x+1]][1];
		while(position[0] != waypoints[bestRoute[x]][0] || position[1] != waypoints[bestRoute[x]][1]){
			switch(storageGrid[position[0]][position[1]].parentDirection){
			case NORTH:
				position[1] -= 1;
				break;
			case EAST:
				position[0] += 1;
				break;
			case WEST:
				position[0] -= 1;
				break;
			case SOUTH:
				position[1] += 1;
				break;
			}

			test = position[0];
			test = test << 8 | position[1];
			output.write(test);
		}
	}
}

uint12 aStarSearch(uint8 startX, uint8 startY, uint8 destX, uint8 destY){
	// Reset the costs and list memberships for all the nodes in the world
	for(int i =0; i < 60; i++){
		for(int j =0; j < 60; j++){
			storageGrid[i][j].cost = 0;
			storageGrid[i][j].listMembership = 0;
		}
	}
	storageGrid[startX][startY].listMembership = 1;

	bool openListEmpty = 0;
	mainAStarLoop: while(openListEmpty == 0){
		uint12 lowestCost = 3600;
		uint8 position[2];
		openListEmpty = 1;
		for(int x = 0; x < worldSize; x++){
			for(int y = 0; y < worldSize; y++){
				if(storageGrid[x][y].listMembership == 1){
					openListEmpty = 0;
					uint12 currentNodeCost = storageGrid[x][y].cost + manhattanDistance(x, y,destX,destY);
					if(currentNodeCost < lowestCost){
						position[0] = x;
						position[1] = y;
						lowestCost = currentNodeCost;
					}
				}
			}
		}
		storageGrid[position[0]][position[1]].listMembership = 2;
		if(position[0] == destX && position[1] == destY){
			return lowestCost;
		}
		checkAndUpdateNode(position[0], position[1] +1, storageGrid[position[0]][position[1]].cost, NORTH); // SOUTH
		checkAndUpdateNode(position[0] + 1, position[1], storageGrid[position[0]][position[1]].cost, WEST); // EAST
		checkAndUpdateNode(position[0] - 1, position[1], storageGrid[position[0]][position[1]].cost, EAST); // WEST
		checkAndUpdateNode(position[0], position[1] -1, storageGrid[position[0]][position[1]].cost, SOUTH); // NORTH
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

