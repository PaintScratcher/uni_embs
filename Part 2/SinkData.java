// Exam Number: Y0070813
package embs;

public class SinkData {

    // 0 is FirstRX which is when we are expecting to receive the first synchronisation pulse
    // 1 is SecondRX which is we are expecting to receive the second synchronisation pulse
    // 2 is FirstTX which is when we are going to transmit for the first time
    // 3 is NCalc which is when we are listening for the first synchronisation pulse of the sequence to ascertain the n value
    // 4 is SecondTX which is when we will continue transmitting continuously 

    protected int state = 0; // Store the state of the sink and default it to the initial state of listening for the first received token
    protected byte firstRecieve; // Store the first value received
    protected long NCalcFireTime; // Store the time when we need to fire to find the value of n
    protected long t; // Store the sinks t value
    protected int n; // Store the sinks n value
}
