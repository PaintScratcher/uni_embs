	#include "toplevel.h"

int main() {
	hls::stream<uint32> to_hw, from_hw;
	int resultGrid[10][10];
	//Create input data
	int inputgrid[10][10] = {
			{0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,1,0,0,0,0,0},
			{0,0,0,1,1,1,0,0,0,0},
			{0,0,0,1,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0}
	};

	for(int h = 0; h < 10; h++){
		for(int w = 0; w < 10; w++){
			printf("%d", inputgrid[h][w]);
			to_hw.write(inputgrid[h][w]);
		}
		printf("\n");
	}
	printf("RESULT:\n");
	//Run the hardware
	toplevel(to_hw, from_hw);
	for(int h = 0; h < 10; h++){
		for(int w = 0; w < 10; w++){
			resultGrid[h][w] = from_hw.read();
			printf("%d",resultGrid[h][w]);
		}
		printf("\n");
	}

	//Read and report the output
	//    int sum = from_hw.read();
	//    int sub = from_hw.read();

	//Check values
	//    if(sum == 2780 && sub == 1220) {
	//        return 0;
	//    } else {
	//        return 1; //An error!
	//    }
}


