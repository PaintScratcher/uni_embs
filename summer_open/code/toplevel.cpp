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
				storageGrid[receiveBuffer[0] + i][receiveBuffer[1]].isWall = 1;
			}
		}
		else{
			for(int i = 0; i < receiveBuffer[3]; i++){
				storageGrid[receiveBuffer[0]][receiveBuffer[1] + i].isWall = 1;
			}
		}
	}

	waypointReadLoop: for(int i = 0; i < numberOfWaypoints; i++) {
		receiveBuffer = input.read();
		waypoints[i][0] = receiveBuffer[0];
		waypoints[i][1] = receiveBuffer[1];
	}

	numberOfWaypointsLoop: for(int waypoint = 0; waypoint < numberOfWaypoints; waypoint++) {
		storageGrid[waypoints[waypoint][0]][waypoints[waypoint][1]].listMembership = 1;
		storageGrid[waypoints[waypoint][0]][waypoints[waypoint][1]].cost = 0;

		bool openListEmpty = 0;
		mainAStarLoop: while(openListEmpty == 0){
			int12 lowestCost = 0;
			Node lowestCostNode;
			openListEmpty = 1;
			for(int x = 0; x < worldSize; x++){
				for(int y = 0; y < worldSize; y++){
					if(storageGrid[x][y].listMembership == 1){
						openListEmpty = 0;
						if(storageGrid[x][y].cost < lowestCost){
							lowestCostNode = storageGrid[x][y];
						}
					}
				}
			}

		}

	}
}

