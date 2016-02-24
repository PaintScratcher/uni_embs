#include "toplevel.h"

int main() {
	hls::stream<uint32> to_hw, from_hw;
	//Create input data
	char input[] = "This is my entry into the EMBS MD5 challenge";
	for(int i = 0; i < sizeof(input); i++){
		to_hw.write(input[i]);
	}


	//Run the hardware
	toplevel(to_hw, from_hw);
	for (int i = 0; i < 16; i++){
		int result = from_hw.read();
		printf("%02x", result);
	}
}