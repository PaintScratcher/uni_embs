#include "toplevel.h"

//Prototypes

//Top-level function
void toplevel(hls::stream<uint32> &input, hls::stream<uint32> &output) {
#pragma HLS RESOURCE variable=input core=AXI4Stream
#pragma HLS RESOURCE variable=output core=AXI4Stream
#pragma HLS INTERFACE ap_ctrl_none port=return
	int32 firstValue = input.read();
	int32 secondValue = input.read();

	int32 result = firstValue / secondValue;

	output.write(result);
}