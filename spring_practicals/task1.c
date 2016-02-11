/*
 * main.c
 *
 *  Created on: 10 Feb 2016
 *      Author: at895
 */
#include <stdio.h>
#include "xgpio_l.h"
#include "xparameters.h"

int RegOffset = 0;
int west = 0; // bit 3
int east = 0; // bit 2
int north = 0; // bit 1

int main() {
	while(1){
		//Set the LED's to the switch state
		int LEDState = checkSwitchState() + checkButtonState();
		XGpio_WriteReg(XPAR_LEDS_8BIT_BASEADDR,RegOffset, LEDState);
	}
    return 0;
}

int checkSwitchState(){
	//Read state of switches
	int switchState = XGpio_ReadReg(XPAR_DIP_SWITCHES_4BIT_BASEADDR, RegOffset);
	//Get the last four bits via a shift
	return switchState << 4;
}

int checkButtonState(){
	int buttonState = XGpio_ReadReg(XPAR_BUTTONS_4BIT_BASEADDR, RegOffset);

	if (buttonState & 0x8){ // Check North
		north = 0x2;
	}
	if (buttonState & 0x4){ // Check East
		east = 1;
	}
	if (buttonState & 0x2){ // Check West
		west = 0x4;
	}
	if (buttonState & 1){
		west = 0;
		east = 0;
		north = 0;
	}
	return north + west + east;
}
