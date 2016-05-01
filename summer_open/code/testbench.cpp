#include "toplevel.h"

int main() {
	hls::stream<uint32> to_hw, from_hw;

	//Create input data
	int6 worldSize = 10;
	int numberofWalls = 6;
	int numberOfWaypoints = 4;
	int distanceMatrix[12][12];

	int waypoints[6][2] = {
			{2, 0},
			{7, 2},
			{2, 3},
			{6, 5},
	};

	int walls[6][4] = {
			{0, 8, 0, 1},
			{5, 3, 1, 3},
			{9, 4, 1, 2},
			{3, 2, 0, 4},
			{4, 5, 1, 4},
			{3, 8, 1, 1},
	};

	//	Write input data
	to_hw.write(worldSize);
	to_hw.write(numberofWalls);
	to_hw.write(numberOfWaypoints);

	for(int i = 0; i < numberOfWaypoints; i++) {
		to_hw.write(waypoints[i][0] << 8 | waypoints[i][1]);
	}
	for(int i = 0; i < numberofWalls; i++) {
		to_hw.write(walls[i][0] << 24 | walls[i][1] << 16 | walls[i][2] << 8 | walls[i][3]);
	}

	//Run the hardware
	toplevel(to_hw, from_hw);

	//Read and report the output
	int numberOfPointsToReceive = from_hw.read();
	printf("%x\n\r", numberOfPointsToReceive);
	for (int i = 0; i < numberOfPointsToReceive; i++){
		printf("%x\n\r", (int)from_hw.read());
	}
}

