#include <stdio.h>
#include "xgpio_l.h"
#include "xparameters.h"
#include "xuartlite_l.h" // This is an L (for low-level), not a 1
#include <stdlib.h>

char receive(){
	char recieved = XUartLite_RecvByte(XPAR_RS232_DTE_BASEADDR);
	xil_printf("%c", recieved);
	return recieved;
}


int main() {
	int state = 0;
	int firstNumber = 0;
	int secondNumber = 0;
	char operator;

	while(1){
		char received = receive();
		if (received == ' '){
			if (state == 2){ // Calculate
				int result;
				switch (operator)
				{
				case '+':
					result = firstNumber + secondNumber;
				  	break;
				case '-':
					result = firstNumber - secondNumber;
					break;
				case '*':
					result = firstNumber * secondNumber;
					break;
				case '/':
					result = firstNumber / secondNumber;
					break;
				}
				xil_printf("%d", result);
				state = 0;
				firstNumber = 0;
				secondNumber = 0;
				xil_printf("%s", "\n\r");
			}
			else{
				state++;
			}
		}
		else {
			if (state < 1){
				firstNumber = (firstNumber * 10) + (received - '0');
			}
			else if(state == 2){
				secondNumber = (secondNumber * 10) + (received - '0');
			}
			else{
				operator = received;
			}

		}
	}
    return 0;
}
