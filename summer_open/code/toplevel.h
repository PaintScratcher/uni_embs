#ifndef __TOPLEVEL_H_
#define __TOPLEVEL_H_

#include <stdio.h>
#include <stdlib.h>
#include <ap_int.h>
#include <hls_stream.h>

//Typedefs
typedef ap_uint<32> uint32;
typedef ap_int<32> int32;
typedef ap_int<1> int1;
typedef ap_int<2> int2;
typedef ap_int<4> int4;
typedef ap_int<8> int8;
typedef ap_int<6> int6;
typedef ap_int<12> int12;
typedef ap_int<32> int32;
typedef struct Wall{
   int8 X;
   int8 Y;
   int1 direction;
   int6 length;
};
typedef struct Node{
   int12 cost = 0;
   int2 listMembership = 0; // 0 is no group, 1 is open and 2 is closed
   int1 isWall = 0;
};


//Prototypes
void toplevel(hls::stream<uint32> &input, hls::stream<uint32> &output);

//Size of the example functionality
#define MAXWORLDSIZE 60

#endif

