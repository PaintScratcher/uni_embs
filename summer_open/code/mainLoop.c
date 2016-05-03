#include <stdio.h>
#include "xgpio_l.h"
#include "xparameters.h"
#include "xuartlite_l.h"
#include <stdlib.h>
#include "xemaclite.h"
#include "fsl.h"

// Colour definitions (1 pixel per byte)
#define BLACK   0b00000000
#define WHITE   0b00000111
#define RED     0b00000100
#define GREEN   0b00000010
#define BLUE    0b00000001
#define CYAN    0b00000011
#define YELLOW  0b00000110
#define MAGENTA 0b00000101
#define FRAME_BUFFER XPAR_DDR_SDRAM_MPMC_BASEADDR + 139264

XEmacLite ether;
static u8 mac_address[] = {0x00, 0x11, 0x22, 0x33, 0x00, 0x02}; // Assigned MAC Address
u8 tmit_buffer[XEL_MAX_FRAME_SIZE]; // Buffer for transmitting to Ethernet
u8 recv_buffer[XEL_MAX_FRAME_SIZE]; // Buffer for receiving from Ethernet
int worldID; // WorldID provided by the user
int worldSize; // Size provided by the user
int worldWidth; // Width received via Ethernet from the server
int worldHeight; // World height received via Ethernet from the server
int numberOfWaypoints; // Number of waypoints in the world
int numberOfWalls; // Number of walls in the world
int gridSize; // Size of each grid unit, used for drawing to VGA

char receive(){
	//
	// Helper function to handle receiving from UART and printing back to it for improved user experience
	char recieved = XUartLite_RecvByte(XPAR_RS232_DTE_BASEADDR);
	xil_printf("%c", recieved);
	return recieved;
}
int main() {
	// Configure ethernet
	XEmacLite_Config *etherconfig = XEmacLite_LookupConfig(XPAR_EMACLITE_0_DEVICE_ID);
	XEmacLite_CfgInitialize(&ether, etherconfig, etherconfig->BaseAddress);
	XEmacLite_SetMacAddress(&ether, mac_address); //Set our sending MAC address
	XEmacLite_FlushReceive(&ether); //Clear any received messages

	xil_printf("\r\n%s", "Please input the desired world size:");
//	receiveWorldInfo(); // Receive the world information from the user via UART
	int worldLoop, i;
	worldSize = 0;
	for(worldLoop = 0; worldLoop < 99999; worldLoop++){
		XEmacLite_FlushReceive(&ether); //Clear any received messages

		tmit_buffer[14] = 0x01; // Set the type field
		tmit_buffer[15] = worldSize; // Add the world size to the transmit buffer
		worldID = worldLoop;
		for(i = 0; i < 4; i++){ // Add the world ID to the transmit, as it is over several bytes we need to loop through and bit shift
			tmit_buffer[16 + i] = worldLoop >> 8*(3-i);
		}
		sendToEthernet(6); // Send the generated buffer to the server via ethernet

	receiveFromEthernet();

	receiveFromHardware();
	receiveFromEthernet();
	}
	return 0;
}

void receiveWorldInfo(){
	// Function to handle integration with the user via UART
	int state = 0; // Store the state of the UART integration
	while(1){
		if (XUartLite_IsReceiveEmpty(XPAR_RS232_DTE_BASEADDR) == 0){
			char received = receive();
			if (state == 0){ // If this is the first character to be received
				worldSize = received - '0'; // We have received the world size
				state++; // Increment the state
				xil_printf("%s", "\r\nPlease input the desired world ID: ");
			}
			else{ // We are receiving worldID information
				if (received == '\r'){ // User has pressed return, indicating end of the worldID.
					int i;
					tmit_buffer[14] = 0x01; // Set the type field
					tmit_buffer[15] = worldSize; // Add the world size to the transmit buffer
					for(i = 0; i < 4; i++){ // Add the world ID to the transmit, as it is over several bytes we need to loop through and bit shift
						tmit_buffer[16 + i] = worldID >> 8*(3-i);
					}
					sendToEthernet(6); // Send the generated buffer to the server via ethernet
					return;
				}
				else{ // User has entered a character other than return so we are still receiving the worldID
					worldID = (worldID * 10) + (received - '0'); // Decimal representation so each successive character requires timesing by 10
				}
			}
		}
	}
}

void sendToEthernet(int messageSize) {
	// Function to facilitate constructing and sending a message to ethernet
	int i;
	u8 *buffer = tmit_buffer;
	//Write the destination MAC address of the server being communicated with
	*buffer++ = 0x00;
	*buffer++ = 0x11;
	*buffer++ = 0x22;
	*buffer++ = 0x44;
	*buffer++ = 0x00;
	*buffer++ = 0x50;

	//Write the source MAC address
	for(i = 0; i < 6; i++)
		*buffer++ = mac_address[i];

	//Write the type field
	*buffer++ = 0x55;
	*buffer++ = 0xAB;

	//Send the buffer
	//The size argument is the data bytes + XEL_HEADER_SIZE which is defined
	//as the size of the destination MAC plus the type/length field
	XEmacLite_FlushReceive(&ether); //Clear any received messages
	XEmacLite_Send(&ether, tmit_buffer, messageSize + XEL_HEADER_SIZE);
}

void receiveFromEthernet(){
	// Function to receive
	while (1){
		int i;
		volatile int recv_len = 0;
		recv_len = XEmacLite_Recv(&ether, recv_buffer); // Store the length of the received packet
		if (recv_len == 0) continue; // If nothing has been received, ignore
		int type = (recv_buffer[12] << 8) | recv_buffer[13]; // Reconstruct the type field from its two bytes
		if (type != 0x55AB) continue; // Check to see if the received packet is of the expected type, ignore it if it is not
		if (recv_buffer[14] == 0x02){ // If the packet received is of the 'ReplyWorld' type
			worldWidth = recv_buffer[19];  // Store the world width from the packet
			worldHeight = recv_buffer[20]; // Store the world height from the packet
			drawGrid();
			putfslx(worldHeight, 0, FSL_DEFAULT); // Send the world grid size to the hardware component (worlds are square)
			numberOfWaypoints = recv_buffer[21]; // Store the number of waypoints from the packet
			putfslx(numberOfWaypoints, 0, FSL_DEFAULT); // Send the number of waypoints to the hardware
			for (i = 0; i < numberOfWaypoints * 2; i = i +2){ // For each of the waypoints, draw it on VGA and send it to the hardware
				if(i == 0){
					drawRectInGrid(recv_buffer[22 + i],recv_buffer[22 + i + 1],gridSize,gridSize,YELLOW); // Draw the first waypoint on the screen
				}
				else{
					drawRectInGrid(recv_buffer[22 + i],recv_buffer[22 + i + 1],gridSize,gridSize,GREEN); // Draw the waypoint on the screen
				}
				putfslx(recv_buffer[22 + i] << 8 | recv_buffer[22 + i + 1], 0, FSL_DEFAULT); // Send the waypoint to the hardware
			}
			int numberOfWallsLocation;
			numberOfWallsLocation = 22 + (numberOfWaypoints * 2); // Calculate the index of the number of walls byte
			numberOfWalls = recv_buffer[numberOfWallsLocation]; // Store the number of walls from the packet
			putfslx(numberOfWalls, 0, FSL_DEFAULT); // Send the number of walls to the hardware
			for (i = 1; i < numberOfWalls * 4; i = i + 4){ // For each of the walls, draw it to the VGA and send it to the hardware
				putfslx(recv_buffer[numberOfWallsLocation + i] << 24 | recv_buffer[numberOfWallsLocation + i + 1] << 16 | recv_buffer[numberOfWallsLocation + i + 2] << 8 | recv_buffer[numberOfWallsLocation + i + 3], 0, FSL_DEFAULT);
				if (recv_buffer[numberOfWallsLocation + i + 2] == 0){ // If the wall is horizontal
					drawRectInGrid(recv_buffer[numberOfWallsLocation + i],recv_buffer[numberOfWallsLocation + i + 1],recv_buffer[numberOfWallsLocation + i + 3] * gridSize,gridSize,BLUE); // Draw the wall segment on the screen
				}
				else if (recv_buffer[numberOfWallsLocation + i + 2] == 1){ // If the wall is vertical
					drawRectInGrid(recv_buffer[numberOfWallsLocation + i],recv_buffer[numberOfWallsLocation + i + 1],gridSize,recv_buffer[numberOfWallsLocation + i + 3] * gridSize,BLUE); // Draw the wall segment on the screen
				}
			}
		}
		else if(recv_buffer[14] == 0x04){ // If the packet received is of the 'SolutionReply' type
			if(recv_buffer[15] == 0x00){ // Check if the solution is correct
				xil_printf("\r\nAnswer is correct");
			}
			else if(recv_buffer[15] == 0x01){
				xil_printf("\r\nAnswer is too long");
			}
			else if(recv_buffer[15] == 0x02){
				xil_printf("\r\nAnswer is too short");
			}
		}
		drawRect(600,0,200,600,BLACK); // Draw a rectangle to the right side of the grid to overwrite any walls that are larger than the grid size
		return;
	}
}
void receiveFromHardware(){
	// Function to handle receiving a response from the hardware component and ask the server if the solution is correct via ethernet
	int numberOfPointsToReceive, i, received;
	getfslx(numberOfPointsToReceive, 0, FSL_DEFAULT); // Get the number of points there are in the solution
	xil_printf("\r\n%x WorldID%d",numberOfPointsToReceive, worldID);
	tmit_buffer[14] = 0x03; // Set the type field of the packet to 'SolveWorld'
	tmit_buffer[15] = worldSize; // Set the size of the world
	for(i = 0; i < 4; i++){ // For each byte in the worldID, add it to the transmit buffer
		tmit_buffer[16 + i] = worldID >> 8*(3-i);
	}
	tmit_buffer[20] = 0x00; // The solution is found with walls included, so we disable the Ignores walls flag
	for(i = 0; i < 4; i++){ // Write the solution cost to the transmit buffer
		tmit_buffer[21 + i] = numberOfPointsToReceive >> 8*(3-i);
	}
	sendToEthernet(11); // Send the transmit buffer to the server via ethernet
	for (i = 0; i < numberOfPointsToReceive; i++){ // For each of the points in the solution route, print it to the VGA
		getfslx(received, 0, FSL_DEFAULT); // Get the point to be printed
		drawWaypointInGrid(received >> 8, received & 0xFF, gridSize/2, gridSize/2,RED); // Display to the VGA
	}
}

void drawGrid(){
	// Function to facilitate drawing a grid to the VGA
	*((volatile unsigned int *) XPAR_EMBS_VGA_0_BASEADDR) = FRAME_BUFFER;
	*((volatile unsigned int *) XPAR_EMBS_VGA_0_BASEADDR + 1) = 1;
	drawRect(0,0,600,600,WHITE);
	drawRect(600,0,200,600,BLACK);
	gridSize = 600 /  worldWidth;
	int loc;
	for (loc = 0; loc < 600; loc = loc + gridSize){
		drawRect(0,loc,600,1,BLACK);
		drawRect(loc,0,1,600,BLACK);
	}
}

void drawRect(int xLoc, int yLoc, int width, int height, u8 colour) {
	// Draws a rectangle of solid colour on the screen
	int x, y;

	for (y = yLoc; y < yLoc + height; y++) {
		for (x = xLoc; x < xLoc + width; x++) {
			*((volatile u8 *) FRAME_BUFFER + x + (800 * y)) = colour;
		}
	}
}
void drawRectInGrid(int xLoc, int yLoc, int width, int height, u8 colour) {
	// Draws a rectangle of solid colour on the screen with correct grid offsets
	int x, y;
	yLoc*=gridSize;
	xLoc*=gridSize;
	for (y = yLoc; y < yLoc + height; y++) {
		for (x = xLoc; x < xLoc + width; x++) {
			*((volatile u8 *) FRAME_BUFFER + x + (800 * y)) = colour;
		}
	}
}
void drawWaypointInGrid(int xLoc, int yLoc, int width, int height, u8 colour) {
	// Draws a rectangle of solid colour on the screen with correct grid offsets
	int x, y;
	yLoc*=gridSize;
	xLoc*=gridSize;
	yLoc += gridSize / 4;
	xLoc += gridSize / 4;
	for (y = yLoc; y < yLoc + height; y++) {
		for (x = xLoc; x < xLoc + width; x++) {
			*((volatile u8 *) FRAME_BUFFER + x + (800 * y)) = colour;
		}
	}
}

