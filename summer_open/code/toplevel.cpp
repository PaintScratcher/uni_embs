#include "toplevel.h"

//Input data storage
int8 distanceMatrix[12][12];
int6 numberOfWalls;
int6 numberOfWaypoints;
int6 worldSize;
int32 receiveBuffer;
Node storageGrid[60][60];
Wall walls[12];
int8 waypoints[12][2];
//Prototypes
void checkAndUpdateNode(int8 X, int8 Y, int12 cost, int8 parent[]);
int12 aStarSearch(int8 startX, int8 startY, int8 destX, int8 destY);

int12 manhattanDistance(int8 X1, int8 Y1, int8 X2, int8 Y2){
	return (abs(X1 - X2) + abs(Y1 - Y2));
}
//Top-level function
void toplevel(hls::stream<uint32> &input, hls::stream<uint32> &output) {
#pragma HLS RESOURCE variable=input core=AXI4Stream
#pragma HLS RESOURCE variable=output core=AXI4Stream
#pragma HLS INTERFACE ap_ctrl_none port=return

	//Read in the data

	worldSize = input.read();
	numberOfWalls = input.read();
	numberOfWaypoints = input.read();
	wallReadLoop:	for(int wallLoopCount = 0; wallLoopCount < numberOfWalls; wallLoopCount++) {
		receiveBuffer = input.read();
		int8 wall[4];
		wall[0] = (int8) (receiveBuffer >> 24);
		wall[1] = (int8) (receiveBuffer >> 16);
		wall[2] = (int8) (receiveBuffer >> 8);
		wall[3] = (int8) receiveBuffer;
		if(wall[2] == 0){
			for(int i = 0; i < wall[3]; i++){
				storageGrid[wall[0] + i][wall[1]].isWall = 1;
			}
		}
		else{
			for(int i = 0; i < wall[3]; i++){
				storageGrid[wall[0]][wall[1] + i].isWall = 1;
			}
		}
	}

	waypointReadLoop: for(int i = 0; i < numberOfWaypoints; i++) {
		receiveBuffer = input.read();
		waypoints[i][0] = (int8) (receiveBuffer >> 8);
		waypoints[i][1] = (int8) receiveBuffer;
	}

	// Loop through the waypoints and populte the distance matrix by performing A* search
	numberOfWaypointsLoop: for(int waypoint = 0; waypoint < numberOfWaypoints; waypoint++) {
		for(int destinationWaypoint = 0; destinationWaypoint < numberOfWaypoints; destinationWaypoint++){
			if(waypoint == destinationWaypoint)continue;

			// Reset the costs and list memberships for all the nodes in the world
			for(int i =0; i < 60; i++){
				for(int j =0; j < 60; j++){
					storageGrid[i][j].cost = 0;
					storageGrid[i][j].listMembership = 0;
				}
			}
			// Find the best distance between waypoints using A* and store the result
			int12 aStarResult = aStarSearch(waypoints[waypoint][0], waypoints[waypoint][1], waypoints[destinationWaypoint][0], waypoints[destinationWaypoint][1]);
			distanceMatrix[waypoint][destinationWaypoint] = aStarResult;
			distanceMatrix[destinationWaypoint][waypoint] = aStarResult;
		}
	}

	// Permutate through all the possibilities of waypoints to find the best route
	int12 lowestCost = 3600;
	int6 bestRoute[13];

	int6 waypointsToPermute[11];
	int N = numberOfWaypoints -1;
	int6 p[12];
	for(int i = 0; i < N; i++){
		waypointsToPermute[i] = i + 1;
		p[i] = i;
	}
	p[N] = N;
	int i = 1;
	int j, temp;
	while(i < N){
		p[i]--;
		j = i % 2 * p[i];
		temp = waypointsToPermute[j];
		waypointsToPermute[j] = waypointsToPermute[i];
		waypointsToPermute[i] = temp;
		//DO the thing here
		int12 currentRouteCost = 0;
		currentRouteCost += distanceMatrix[0][waypointsToPermute[0]];
		for(int x = 0; x < numberOfWaypoints -1; x++){
			currentRouteCost += distanceMatrix[waypointsToPermute[i]][waypointsToPermute[i+1]];
		}
		currentRouteCost += distanceMatrix[N][0];
		if(currentRouteCost < lowestCost){
			lowestCost = currentRouteCost;
			bestRoute[0] = 0;
			for(int x = 0; x < numberOfWaypoints; x++){
				bestRoute[x + 1] = waypointsToPermute[x];
			}
			bestRoute[numberOfWaypoints] = 0;
		}

//		for (int x = 0; x < 12; x++){
//			printf("%d ",(int)waypointsToPermute[x]);
//		}
//		printf("\n");
		i = 1;
		while(!p[i]){
			p[i] = i;
			i++;
		}
	}
	for (int x = 0; x < 12; x++){
		printf("%d",(int)bestRoute[x]);
	}
	int8 position[2],tempPosition[2];
	for(int x = 0; x < numberOfWaypoints; x++){
		aStarSearch(waypoints[bestRoute[x]][0], waypoints[bestRoute[x]][1], waypoints[bestRoute[x+1]][0], waypoints[bestRoute[x+1]][1]);
		position[0] = waypoints[bestRoute[x+1]][0];
		position[1] = waypoints[bestRoute[x+1]][1];
		while(position[0] != waypoints[bestRoute[x]][0] || position[1] != waypoints[bestRoute[x]][1]){
			tempPosition[0] = storageGrid[position[0]][position[1]].parent[0];
			tempPosition[1] = storageGrid[position[0]][position[1]].parent[1];
			position[0] = tempPosition[0];
			position[1] = tempPosition[1];
			printf("\r\n(%d,%d)",(int)position[0],(int)position[1]);
//			output.write((int)position);
		}
	}

	// Output the distance matrix for testing in the testbench
	for (int x = 0; x < 12; x++){
		for (int y = 0; y < 12; y++){
			output.write((int)distanceMatrix[x][y]);
		}
	}
}

int12 aStarSearch(int8 startX, int8 startY, int8 destX, int8 destY){
	storageGrid[startX][startY].listMembership = 1;
	storageGrid[startX][startY].cost = 0;
	storageGrid[startX][startY].parent[0] = 0;
	storageGrid[startX][startY].parent[1] = 0;

	bool openListEmpty = 0;
	mainAStarLoop: while(openListEmpty == 0){
		int12 lowestCost = 3600;
		int8 position[2];
		openListEmpty = 1;
		for(int x = 0; x < worldSize; x++){
			for(int y = 0; y < worldSize; y++){
				if(storageGrid[x][y].listMembership == 1){
					openListEmpty = 0;
					int12 currentNodeCost = storageGrid[x][y].cost + manhattanDistance(x, y,destX,destY);
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
		checkAndUpdateNode(position[0], position[1] +1, storageGrid[position[0]][position[1]].cost, position); // NORTH
		checkAndUpdateNode(position[0] + 1, position[1], storageGrid[position[0]][position[1]].cost, position); // EAST
		checkAndUpdateNode(position[0] - 1, position[1], storageGrid[position[0]][position[1]].cost, position); // WEST
		checkAndUpdateNode(position[0], position[1] -1, storageGrid[position[0]][position[1]].cost, position); // SOUTH
	}
}

void checkAndUpdateNode(int8 X, int8 Y, int12 cost, int8 parent[]){
	if(!storageGrid[X][Y].isWall && X >= 0 && Y>=0 && X < worldSize && Y < worldSize){
		if(storageGrid[X][Y].listMembership != 2){
			if(storageGrid[X][Y].listMembership != 1){
				storageGrid[X][Y].listMembership = 1;
				storageGrid[X][Y].cost = cost + 1;
				storageGrid[X][Y].parent[0] = parent[0];
				storageGrid[X][Y].parent[1] = parent[1];
			}
			else if(storageGrid[X][Y].cost > cost + 1){
				storageGrid[X][Y].cost = cost + 1;
				storageGrid[X][Y].parent[0] = parent[0];
				storageGrid[X][Y].parent[1] = parent[1];
			}
		}
	}
}


