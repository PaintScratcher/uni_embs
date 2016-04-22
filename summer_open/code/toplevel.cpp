#include "toplevel.h"

//Input data storage
int32 inputdata[NUMDATA];

//Prototypes

//Top-level function
void toplevel(hls::stream<uint32> &input, hls::stream<uint32> &output) {
#pragma HLS RESOURCE variable=input core=AXI4Stream
#pragma HLS RESOURCE variable=output core=AXI4Stream
#pragma HLS INTERFACE ap_ctrl_none port=return

    //Read in NUMDATA items
    readloop: for(int i = 0; i < NUMDATA; i++) {
        inputdata[i] = input.read();
    }

}


