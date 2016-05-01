#include <stdio.h>
#include "xgpio_l.h"
#include "xparameters.h"
#include "xuartlite_l.h" // This is an L (for low-level), not a 1
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
static u8 mac_address[] = {0x00, 0x11, 0x22, 0x33, 0x00, 0x02}; // Remember to change this to *your* MAC address!
u8 tmit_buffer[XEL_MAX_FRAME_SIZE];
u8 recv_buffer[XEL_MAX_FRAME_SIZE];
int currentBufferIndex = 16;
int state = 0;
int worldID;
int worldSize;
int worldWidth;
int worldHeight;
int numberOfWaypoints;
int numberOfWalls;
int waypoints[12][2];
int walls[20][4];
int gridSize;
char receive(){
	char recieved = XUartLite_RecvByte(XPAR_RS232_DTE_BASEADDR);
	xil_printf("%c", recieved);
	return recieved;
}
int main() {
	XEmacLite_Config *etherconfig = XEmacLite_LookupConfig(XPAR_EMACLITE_0_DEVICE_ID);
	XEmacLite_CfgInitialize(&ether, etherconfig, etherconfig->BaseAddress);
	XEmacLite_SetMacAddress(&ether, mac_address); //Set our sending MAC address
	XEmacLite_FlushReceive(&ether); //Clear any received messages
	xil_printf("\r\n%s", "Please input the desired world size:");


	recieveWorldInfo();
	recieveFromEthernet();
	drawWorld();
	recieveFromHardware();
	recieveFromEthernet();

	return 0;
}

void recieveWorldInfo(){
	while(1){
		if (XUartLite_IsReceiveEmpty(XPAR_RS232_DTE_BASEADDR) == 0){
			char received = receive();
			if (state == 0){
				worldSize = received - '0';
				state++;
				xil_printf("%s", "\r\nPlease input the desired world ID: ");
			}
			else{
				if (received == 'a'){
					int i;
					tmit_buffer[14] = 0x01;
					tmit_buffer[15] = worldSize;
					for(i = 0; i < 4; i++){
						tmit_buffer[16 + i] = worldID >> 8*(3-i);
					}
					sendToEthernet(6);
					return;
				}
				else{
					worldID = (worldID * 10) + (received - '0');
				}
			}
		}
	}
}

void sendToEthernet(int messageSize) {
	int i;
	u8 *buffer = tmit_buffer;
	//Write the destination MAC address (broadcast in this case)
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

void recieveFromEthernet(){
	while (1){
		int i;
		volatile int recv_len = 0;
		recv_len = XEmacLite_Recv(&ether, recv_buffer);
		int type = (recv_buffer[12] << 8) | recv_buffer[13];
		if (type != 0x55AB) continue;
		if (recv_len == 0) continue;
		if (recv_buffer[14] == 0x02){
			int wayPointCounter = 0;
			int wallCounter = 0;
			worldWidth = recv_buffer[19];
			worldHeight = recv_buffer[20];
			putfslx(worldHeight, 0, FSL_DEFAULT);
			numberOfWaypoints = recv_buffer[21];
			putfslx(numberOfWaypoints, 0, FSL_DEFAULT);
			for (i = 0; i < numberOfWaypoints * 2; i = i +2){
				waypoints[wayPointCounter][0] = recv_buffer[22 + i]; // X Location
				waypoints[wayPointCounter][1] = recv_buffer[22 + i + 1]; // Y Location
				//				xil_printf("\r\nWaypoint= (%d,%d)", recv_buffer[22 + i],recv_buffer[22 + i + 1]);
				putfslx(recv_buffer[22 + i] << 8 | recv_buffer[22 + i + 1], 0, FSL_DEFAULT);
				wayPointCounter++;
			}
			int numberOfWallsLocation;
			numberOfWallsLocation = 22 + (numberOfWaypoints * 2);
			numberOfWalls = recv_buffer[numberOfWallsLocation];
			putfslx(numberOfWalls, 0, FSL_DEFAULT);
			for (i = 1; i < numberOfWalls * 4; i = i + 4){
				putfslx(recv_buffer[numberOfWallsLocation + i] << 24 | recv_buffer[numberOfWallsLocation + i + 1] << 16 | recv_buffer[numberOfWallsLocation + i + 2] << 8 | recv_buffer[numberOfWallsLocation + i + 3], 0, FSL_DEFAULT);
				//				xil_printf("\r\nWall= %d%d%d%d",recv_buffer[numberOfWallsLocation + i],recv_buffer[numberOfWallsLocation + i + 1],recv_buffer[numberOfWallsLocation + i + 2],recv_buffer[numberOfWallsLocation + i + 3]);
				walls[wallCounter][0] = recv_buffer[numberOfWallsLocation + i]; // X Location
				walls[wallCounter][1] = recv_buffer[numberOfWallsLocation + i + 1];// Y Location
				walls[wallCounter][2] = recv_buffer[numberOfWallsLocation + i + 2];// Direction (0 = horizontal, 1 = vertical)
				walls[wallCounter][3] = recv_buffer[numberOfWallsLocation + i + 3];// Length
				wallCounter++;
			}
			return;
		}
		else if(recv_buffer[14] == 0x04){
			if(recv_buffer[15] == 0x00){
				xil_printf("\r\nAnswer is correct");
			}
			else if(recv_buffer[15] == 0x01){
				xil_printf("\r\nAnswer is too long");
			}
			else if(recv_buffer[15] == 0x02){
				xil_printf("\r\nAnswer is too short");
			}
		}
	}
}
void recieveFromHardware(){
	int numberOfPointsToReceive, i, received;
	getfslx(numberOfPointsToReceive, 0, FSL_DEFAULT);
	xil_printf("\r\n%x",numberOfPointsToReceive);
	tmit_buffer[14] = 0x03;
	tmit_buffer[15] = worldSize;
	for(i = 0; i < 4; i++){
		tmit_buffer[16 + i] = worldID >> 8*(3-i);
	}
	tmit_buffer[20] = 0x00;
	for(i = 0; i < 4; i++){
		tmit_buffer[21 + i] = numberOfPointsToReceive >> 8*(3-i);
	}
	sendToEthernet(11);
	//	for (i = 0; i < numberOfPointsToReceive; i++){
	//		getfslx(received, 0, FSL_DEFAULT);
	//		xil_printf("\r\n%x",received);
	//	}
}

void drawWorld(){
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
	int i;
	for(i = 0; i < numberOfWaypoints; i++){
		drawRectInGrid(waypoints[i][0],waypoints[i][1],gridSize,gridSize,GREEN);
	}
	for(i = 0; i < numberOfWalls; i++){
		//		xil_printf("\r\nWall Direction: %d",walls[i][2]);
		if (walls[i][2] == 0){
			drawRectInGrid(walls[i][0],walls[i][1],walls[i][3] * gridSize,gridSize,BLUE);
		}
		else if (walls[i][2] == 1){
			drawRectInGrid(walls[i][0],walls[i][1],gridSize,walls[i][3] * gridSize,BLUE);
		}
	}
	drawRect(600,0,200,600,BLACK);
}

// Draws a rectangle of solid colour on the screen
void drawRect(int xLoc, int yLoc, int width, int height, u8 colour) {
	int x, y;

	for (y = yLoc; y < yLoc + height; y++) {
		for (x = xLoc; x < xLoc + width; x++) {
			*((volatile u8 *) FRAME_BUFFER + x + (800 * y)) = colour;
		}
	}
}
void drawRectInGrid(int xLoc, int yLoc, int width, int height, u8 colour) {
	int x, y;
	yLoc*=gridSize;
	xLoc*=gridSize;
	for (y = yLoc; y < yLoc + height; y++) {
		for (x = xLoc; x < xLoc + width; x++) {
			*((volatile u8 *) FRAME_BUFFER + x + (800 * y)) = colour;
		}
	}
}

