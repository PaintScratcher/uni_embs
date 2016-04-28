#include "toplevel.h"

int main() {
	hls::stream<uint32> to_hw, from_hw;

	//Create input data
	int6 worldSize = 30;
	int numberofWalls = 12;
	int numberOfWaypoints = 10;
	int distanceMatrix[12][12];

	int waypoints[10][2] = {
			{22, 12},
			{19, 27},
			{26, 6},
			{27, 16},
			{28, 17},
			{3, 7},
			{17, 28},
			{25, 18},
			{18, 27},
			{1, 8}
	};

	int walls[12][4] = {
			{0, 28, 0, 5},
			{5, 23, 1, 9},
			{9, 14, 1, 6},
			{23, 22, 0, 9},
			{4, 15, 1, 6},
			{13, 18, 1, 3},
			{2, 10, 1, 7},
			{12, 23, 0, 3},
			{17, 17, 1, 6},
			{15, 23, 0, 5},
			{20, 25, 0, 9},
			{28, 21, 0, 2}
	};
	//	printf("Wall Test %x\n", walls[1][0] << 24 | walls[1][1] << 16 | walls[1][2] << 8 | walls[1][3]);
	//	Write input data

	to_hw.write(worldSize);
	to_hw.write(numberofWalls);
	to_hw.write(numberOfWaypoints);

	for(int i = 0; i < numberofWalls; i++) {
		to_hw.write(walls[i][0] << 24 | walls[i][1] << 16 | walls[i][2] << 8 | walls[i][3]);
	}
	for(int i = 0; i < numberOfWaypoints; i++) {
		to_hw.write(waypoints[i][0] << 8 | waypoints[i][1]);
	}

	//Run the hardware
	toplevel(to_hw, from_hw);

	//Read and report the output
	for (int x = 0; x < 12; x++){
		for (int y = 0; y < 12; y++){
//			distanceMatrix[x][y] = from_hw.read();
			printf("%d     ", (int)from_hw.read());
		}
		printf("\n");
	}
}

