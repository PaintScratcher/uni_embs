#include "toplevel.h"

//Prototypes
int1 currentGeneration[10][10];
int1 nextGeneration[10][10];

int3 calculateNeighbours(int y, int x){
	int3 numberOfNeighbours = (currentGeneration[y][x] != 0) ? -1 : 0;
	neighboursloop1: for (int h = -1; h <= 1; h++) {
		neighboursloop2:for (int w = -1; w <= 1; w++) {
			if (currentGeneration[(10 + (y + h)) % 10][(10 + (x + w)) % 10] != 0) {
				numberOfNeighbours++;
			}
		}
	}
	return numberOfNeighbours;
}

//Top-level function
void toplevel(hls::stream<uint32> &input, hls::stream<uint32> &output) {
#pragma HLS RESOURCE variable=input core=AXI4Stream
#pragma HLS RESOURCE variable=output core=AXI4Stream
#pragma HLS INTERFACE ap_ctrl_none port=return

	readloop1: for(int h = 0; h < 10; h++) {
		readloop2: for(int w = 0; w < 10; w++){
			currentGeneration[h][w] = input.read();
		}
	}

	mainloop1:for (int h = 0; h < 10; h++) {
		mainloop2:for (int w = 0; w < 10; w++) {
			int3 neighbours = calculateNeighbours(h, w);
			if (currentGeneration[h][w] == 1){
				if (neighbours == 2 | neighbours == 3){
					nextGeneration[h][w] = 1;
				}
			}
			else{
				if (neighbours == 3){
					nextGeneration[h][w] = 1;
				}
			}
			output.write(nextGeneration[h][w]);
		}
	}
}