/*
 * main.c
 *
 *  Created on: 11 Feb 2016
 *      Author: at895
 */

#include <stdio.h>
#include "xgpio_l.h"
#include "xparameters.h"
#include "xuartlite_l.h"
#include "xemaclite.h"

XEmacLite ether;
static u8 mac_address[] = {0x00, 0x11, 0x22, 0x33, 0x00, 0x02}; // Remember to change this to *your* MAC address!
u8 tmit_buffer[XEL_MAX_FRAME_SIZE];
u8 recv_buffer[XEL_MAX_FRAME_SIZE];
int currentBufferIndex = 0;
int MAC = 0x00;
int haveMAC = 0;
int main(){
	//Initialise the driver
	XEmacLite_Config *etherconfig = XEmacLite_LookupConfig(XPAR_EMACLITE_0_DEVICE_ID);
	XEmacLite_CfgInitialize(&ether, etherconfig, etherconfig->BaseAddress);
	XEmacLite_SetMacAddress(&ether, mac_address); //Set our sending MAC address
	XEmacLite_FlushReceive(&ether); //Clear any received messages
	while(1){
		if (XUartLite_IsReceiveEmpty(XPAR_RS232_DTE_BASEADDR) == 0){
			recieveFromUART();
		}
		recieveFromEthernet();
	}
	return 0;
}

void recieveFromEthernet(){
	int i;
	volatile int recv_len = 0;
	recv_len = XEmacLite_Recv(&ether, recv_buffer);
	int type = (recv_buffer[12] << 8) | recv_buffer[13];
	if (type != 0x55AA) return;
	int messageLength = (recv_buffer[14]*10) + recv_buffer[15];
	if (recv_len != 0){
		for(i = 16; i < (16 + messageLength); i++) {
			xil_printf("%c", recv_buffer[i]);
		}
		xil_printf("\n\r");
	}
}
void sendToEthernet() {
	int i;
	u8 *buffer = tmit_buffer;
	//Write the destination MAC address (broadcast in this case)
	*buffer++ = 0x00;
	*buffer++ = 0x11;
	*buffer++ = 0x22;
	*buffer++ = 0x33;
	*buffer++ = 0x00;
	*buffer++ = MAC;

	//Write the source MAC address
	for(i = 0; i < 6; i++)
		*buffer++ = mac_address[i];

	//Write the type/length field
	*buffer++ = 0x55;
	*buffer++ = 0xAA;

	//Now fill some data
	for (i = 0; i < 10; i++) {
		*buffer++ = (u8) i;
	}

	//Send the buffer
	//The size argument is the data bytes + XEL_HEADER_SIZE which is defined
	//as the size of the destination MAC plus the type/length field
	XEmacLite_Send(&ether, tmit_buffer, 10 + XEL_HEADER_SIZE);
}
void recieveFromUART(){
	char received = XUartLite_RecvByte(XPAR_RS232_DTE_BASEADDR);

	if (received == '\r'){ // If the enter key has been pressed
		if (haveMAC == 0){
			strcpy(MAC, tmit_buffer);
			haveMAC = 1;
			return;
		}
		sendToEthernet();
		xil_printf("\n\r");
		int i;
		for(i = 0; i < 64; i++){ // Reset Buffer
			tmit_buffer[i] = '\0';
		}
		currentBufferIndex = 0; // Reset buffer index
		haveMAC = 0;
	}
	else{ // Store the string being received
		if (haveMAC == 1){
			xil_printf("%c", received);
		}
		tmit_buffer[currentBufferIndex] = received;
		currentBufferIndex ++;
	}
}
