#include "toplevel.h"

int main() {
	hls::stream<uint32> to_hw, from_hw;
	//Create input data

	to_hw.write(4);
	to_hw.write(2);

	//Run the hardware
	toplevel(to_hw, from_hw);
	int result = from_hw.read();
	printf("%d", result);
}