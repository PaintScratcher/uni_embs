#ifndef __TOPLEVEL_H_
#define __TOPLEVEL_H_

#include <stdio.h>
#include <stdlib.h>
#include <ap_int.h>
#include <hls_stream.h>

//Typedefs
typedef ap_uint<1> uint1;
typedef ap_uint<2> uint2;
typedef ap_uint<8> uint8;
typedef ap_uint<6> uint6;
typedef ap_uint<12> uint12;
typedef ap_uint<32> uint32;

// Define data structure to store wall information
struct Wall{
	uint8 X; // Grid X co-ordinate
	uint8 Y; // Grid Y co-ordinate
	uint1 direction; // Direction of the wall, 0 is horizontal, 1 is vertical
	uint6 length; // Length of the wall, in grid units
};

// Define an enum for east reading of directions in the code
enum Direction
{
	NORTH,
	EAST,
	WEST,
	SOUTH,
};

// Define data structure to store information on A* nodes
struct Node{
	uint12 cost; // Distance cost of the node
	uint2 listMembership; // A* list membership. 0 is no group, 1 is open and 2 is closed
	uint1 isWall; // 0 is default, set to 1 if the current node is a wall
	Direction parentDirection; // Grid direction to find the nodes A* parent node
};

//Prototypes
void toplevel(hls::stream<uint32> &input, hls::stream<uint32> &output);

//Size of the example functionality
#define MAX_WORLD_SIZE 60 // Grid size of the large world
#define MAX_NUMBER_OF_WAYPOINTS 12 // Maximum number of waypoints possible in a large world
#define MAX_NUMBER_OF_WALLS 20 // Maximum number of walls possible in a large world

#endif

