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

//Top-level function
void toplevel(hls::stream<uint32> &input, hls::stream<uint32> &output) {
#pragma HLS RESOURCE variable=input core=AXI4Stream
#pragma HLS RESOURCE variable=output core=AXI4Stream
#pragma HLS INTERFACE ap_ctrl_none port=return

	//Read in the data

	worldSize = input.read();
	numberOfWalls = input.read();
	numberOfWaypoints = input.read();
	wallReadLoop:	for(int wall = 0; wall < numberOfWalls; wall++) {
		receiveBuffer = input.read();
		if(receiveBuffer[2] == 0){
			for(int i = 0; i < receiveBuffer[3]; i++){
				storageGrid[(int)receiveBuffer[0] + i][(int)receiveBuffer[1]].isWall = 1;
			}
		}
		else{
			for(int i = 0; i < receiveBuffer[3]; i++){
				storageGrid[(int)receiveBuffer[0]][(int)receiveBuffer[1] + i].isWall = 1;
			}
		}
	}

	waypointReadLoop: for(int i = 0; i < numberOfWaypoints; i++) {
		receiveBuffer = input.read();
		waypoints[i][0] = receiveBuffer[0];
		waypoints[i][1] = receiveBuffer[1];
	}

	numberOfWaypointsLoop: for(int waypoint = 0; waypoint < numberOfWaypoints; waypoint++) {
		for(int destinationWaypoint = 0; destinationWaypoint < numberOfWaypoints; destinationWaypoint++){
			if(waypoint == destinationWaypoint)continue;

			storageGrid[waypoints[waypoint][0]][waypoints[waypoint][1]].listMembership = 1;
			storageGrid[waypoints[waypoint][0]][waypoints[waypoint][1]].cost = 0;

			bool openListEmpty = 0;
			mainAStarLoop: while(openListEmpty == 0){
				int12 lowestCost = 0;
				int8 position[2];
				openListEmpty = 1;
				for(int x = 0; x < worldSize; x++){
					for(int y = 0; y < worldSize; y++){
						if(storageGrid[x][y].listMembership == 1){
							openListEmpty = 0;
							int12 currentNodeCost = storageGrid[x][y].cost + manhattanDistance(x, y,waypoints[destinationWaypoint][0],waypoints[destinationWaypoint][1]);
							if(currentNodeCost < lowestCost){
								position[0] = x;
								position[1] = y;
								lowestCost = currentNodeCost;
							}
						}
					}
				}
				storageGrid[position[0]][position[1]].listMembership = 2;
				if(position[0] == waypoints[waypoint][0] && position[1] == waypoints[waypoint][1]){
					distanceMatrix[waypoint][destinationWaypoint] = lowestCost;
					distanceMatrix[destinationWaypoint][waypoint] = lowestCost;
					break;
				}
				checkAndUpdateNode(position[0], position[1] +1, lowestCost); // NORTH
				checkAndUpdateNode(position[0] + 1, position[1], lowestCost); // EAST
				checkAndUpdateNode(position[0] - 1, position[1] +1, lowestCost); // WEST
				checkAndUpdateNode(position[0], position[1] -1, lowestCost); // SOUTH
			}
		}
	}
}
void checkAndUpdateNode(int8 X, int8 Y, int12 cost){
	if(!storageGrid[X][Y].isWall){
		if(storageGrid[X][Y].listMembership != 2){
			if(storageGrid[X][Y].listMembership != 1){
				storageGrid[X][Y].listMembership = 1;
			}
			else if(storageGrid[X][Y].cost > cost + 1){
				storageGrid[X][Y].cost = cost + 1;
			}
		}
	}
}

int12 manhattanDistance(int8 X1, int8 Y1, int8 X2, int8 Y2){
	return (abs(X1 - X2) + abs(Y1 - Y2));
}

