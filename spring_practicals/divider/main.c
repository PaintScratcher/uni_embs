#include <stdio.h>
#include <stdlib.h>
#include "xparameters.h"
#include "xparameters.h"
#include "xtmrctr.h"
#include "fsl.h"
#define NUMTESTS 10


int main(void) {

	int i;
	volatile int testdata[NUMTESTS*2] = {20, 2, 28, 7, 10034, 23, 3861, 235, 39316, 384, 20106, 395, 5721, 944, 25, 1, 738, 346, 50205, 19};
	volatile int output[NUMTESTS];
	int expectedresults[NUMTESTS] = {10, 4, 436, 16, 102, 50, 6, 25, 2, 2642};
	XTmrCtr timer;
	XTmrCtr_Initialize(&timer, XPAR_TMRCTR_0_DEVICE_ID);
	XTmrCtr_SetResetValue(&timer, 0, 0);
	XTmrCtr_Reset(&timer, 0); // Reset timer 0
	int val = XTmrCtr_GetValue(&timer, 0); // Read the current value of timer 0

	//Perform the calculation
	XTmrCtr_Start(&timer, 0); // Start timer 0
	for(i = 0; i < NUMTESTS; i++) {
		//output[i] = testdata[i*2] / testdata[i*2+1];
		putfslx(testdata[i*2], 0, FSL_DEFAULT);
		putfslx(testdata[i*2+1], 0, FSL_DEFAULT);
		getfslx(output[i], 0, FSL_DEFAULT);
	}
	XTmrCtr_Stop(&timer, 0); // Stop timer 0

	val = XTmrCtr_GetValue(&timer, 0); // Read the current value of timer 0
	xil_printf("Timer second value: %d\n\r", val); // Prints approx. 450

	//Check and print out the results
	for(i = 0; i < NUMTESTS; i++) {
		xil_printf("%d / %d = %d\n\r", testdata[i*2], testdata[i*2+1], output[i]);
		if(output[i] != expectedresults[i]) {
			xil_printf("ERROR: Expected %d. Calculated %d.", expectedresults[i], output[i]);
		}
	}

	XTmrCtr_Reset(&timer, 0); // Reset timer 0
	val = XTmrCtr_GetValue(&timer, 0); // Read the current value of timer 0

	//Perform the calculation
	XTmrCtr_Start(&timer, 0); // Start timer 0
	for(i = 0; i < NUMTESTS; i++) {
		output[i] = testdata[i*2] / testdata[i*2+1];
//		putfslx(testdata[i*2], 0, FSL_DEFAULT);
//		putfslx(testdata[i*2+1], 0, FSL_DEFAULT);
//		getfslx(output[i], 0, FSL_DEFAULT);
	}
	XTmrCtr_Stop(&timer, 0); // Stop timer 0

	val = XTmrCtr_GetValue(&timer, 0); // Read the current value of timer 0
	xil_printf("Timer second value: %d\n\r", val); // Prints approx. 450

	//Check and print out the results
	for(i = 0; i < NUMTESTS; i++) {
		xil_printf("%d / %d = %d\n\r", testdata[i*2], testdata[i*2+1], output[i]);
		if(output[i] != expectedresults[i]) {
			xil_printf("ERROR: Expected %d. Calculated %d.", expectedresults[i], output[i]);
		}
	}
	return 0;
}

#include <stdio.h>
#include <stdlib.h>
#include "xparameters.h"
#include "xparameters.h"
#include "xtmrctr.h"
#define NUMTESTS 10

int main(void) {
    int i;
    volatile int testdata[NUMTESTS*2] = {20, 2, 28, 7, 10034, 23, 3861, 235, 39316, 384, 20106, 395, 5721, 944, 25, 1, 738, 346, 50205, 19};
    volatile int output[NUMTESTS];
    int expectedresults[NUMTESTS] = {10, 4, 436, 16, 102, 50, 6, 25, 2, 2642};
    XTmrCtr timer;
    XTmrCtr_Initialize(&timer, XPAR_TMRCTR_0_DEVICE_ID);
    XTmrCtr_SetResetValue(&timer, 0, 0);
    XTmrCtr_Reset(&timer, 0); // Reset timer 0
    int val = XTmrCtr_GetValue(&timer, 0); // Read the current value of timer 0

    //Perform the calculation
    XTmrCtr_Start(&timer, 0); // Start timer 0
    for(i = 0; i < NUMTESTS; i++) {
        output[i] = testdata[i*2] / testdata[i*2+1];
    }
    XTmrCtr_Stop(&timer, 0); // Stop timer 0

    val = XTmrCtr_GetValue(&timer, 0); // Read the current value of timer 0
    xil_printf("Timer second value: %d\n\r", val); // Prints approx. 450

    //Check and print out the results
    for(i = 0; i < NUMTESTS; i++) {
        xil_printf("%d / %d = %d\n\r", testdata[i*2], testdata[i*2+1], output[i]);
        if(output[i] != expectedresults[i]) {
            xil_printf("ERROR: Expected %d. Calculated %d.", expectedresults[i], output[i]);
        }
    }
    return 0;
}