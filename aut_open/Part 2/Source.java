// Exam Number: Y0070813
package embs;

//Required Libraries 
import com.ibm.saguaro.logger.*;
import com.ibm.saguaro.system.DevCallback;
import com.ibm.saguaro.system.Device;
import com.ibm.saguaro.system.LED;
import com.ibm.saguaro.system.Mote;
import com.ibm.saguaro.system.Radio;
import com.ibm.saguaro.system.Time;
import com.ibm.saguaro.system.Timer;
import com.ibm.saguaro.system.TimerEvent;
import com.ibm.saguaro.system.Util;
import com.ibm.saguaro.system.csr;

public class Source {
    
    // Timers for waking the mote for each sink
    private static Timer sink0Tmr;
    private static Timer sink1Tmr;
    private static Timer sink2Tmr;   

    private static int DATA_INDEX = 11; // Index of the data byte of a received frame
    private static long TIME_OFFSET = 10; // Amount of time in ms to offset each operation

    static Radio radio = new Radio(); // Initialise the mote radio

    private static SinkData[] sinkStore = new SinkData[3]; // Array for storing information on sinks
    private static byte[] transmitByte; // The byte that we will use for transmitting
    private static byte PAN; // The PAN Address that will be used
    private static byte SHORT_ADDR; // The short address that will be used
    private static byte returnChannel; // The channel that will beset after we have completed a transmit


    static {
	// Method is run when the mote is initialised
	for(int i = 0; i < 3; i++){ // Initialise the sinkStore array by populating it with 3 SinkData objects
	    sinkStore[i] = new SinkData(); // Create a SinkData object for each sink
	}
	sink0Tmr = new Timer(); // Initialise the timer for the first sink
	sink1Tmr = new Timer(); // Initialise the timer for the second sink
	sink2Tmr = new Timer(); // Initialise the timer for the third sink
	sink0Tmr.setParam((byte)0); // Set the parameters for the timers, this will 
	sink1Tmr.setParam((byte)1); // allow us to know which channel to switch to when 
	sink2Tmr.setParam((byte)2); // it fires its callback
	// Set the callbacks for the alarms to the correct method that will handle them
	sink0Tmr.setCallback(new TimerEvent(null){
	    public void invoke(byte param, long time) {
		handleAlarmCallaback(param, time);
	    }
	});
	sink1Tmr.setCallback(new TimerEvent(null){
	    public void invoke(byte param, long time) {
		handleAlarmCallaback(param, time);
	    }
	});
	sink2Tmr.setCallback(new TimerEvent(null){
	    public void invoke(byte param, long time) {
		handleAlarmCallaback(param, time);
	    }
	});       

	radio.open(Radio.DID, null, 0, 0); // Open the default radio
	radio.setRxHandler(new DevCallback(null){ // Set the method to be run when the radio receives a packet
	    public int invoke (int flags, byte[] data, int len, int info, long time) {
		return  Source.handleRX(flags, data, len, info, time);
	    }
	});
	startSequence((byte)0); // Start the algorithm on the first channel
    }

    private static void startSequence(byte channel){
	// Method to start the algorithm for each sink / channel 
	returnChannel = channel; // Set the return channel to the channel so it is returned to if interrupted by a transmit
	setChannel(channel); // Change the radio to the new channel
    }

    private  static int handleRX(int flags, byte[] data, int len, int info, long time){
	//Method to handle a packet being received on the radio
	if (data == null) { // End of transmission
	    return 0;
	}

	byte channel = radio.getChannel(); // Get the channel we are currently listening on
	SinkData sink = sinkStore[channel]; // Get the sink information for the corresponding channel
	switch(sink.state){ // Determine what stage of the system we are at
	case 0: // Stage one, we will be receiving the first synchronisation packet
	    Logger.appendString(csr.s2b("FirstRX on channel: ")); // Debug printing
	    Logger.appendByte(channel);
	    Logger.appendString(csr.s2b(" Recieved: "));
	    Logger.appendByte(data[11]);
	    Logger.flush(Mote.WARN);
	    sink.firstRecieve = data[DATA_INDEX]; // Store the first value we receive
	    sink.t = time; // Store the current time so we can use it for determining t in the next stage
	    sink.state = 1; // Set the sink state to the next stage
	    break;
	case 1: // Stage two, we will be receiving the second synchronisation packet
	    Logger.appendString(csr.s2b("SecondRX on channel: ")); // Debug printing
	    Logger.appendByte(channel);
	    Logger.appendString(csr.s2b(" Recieved: "));
	    Logger.appendByte(data[11]);
	    Logger.flush(Mote.WARN);
	    if (data[11] >= sink.firstRecieve){ // If the value we have received is larger than the value we previously received then we need to rollback to the previous state
		sink.firstRecieve = data[11]; // Store the value we received as if it was the first
		sink.state = 1; // Maintain the current state, to return here upon the next receive
		return 0;
	    }
	    sink.t = (time - sink.t) / (sink.firstRecieve - data[11]); // Set the sinks t value
	    sink.state = 2; // Set the sink state to the next stage
	    setTimer(channel, (sink.t * data[DATA_INDEX]) + Time.toTickSpan(Time.MILLISECS, TIME_OFFSET)); // Set an alarm to wake the mote at the next required time
	    if (channel < 2){ // We no longer need to listen on this channel so we can cycle to listening on the next channel
		startSequence((byte)(channel+1)); // Next sequential channel
	    }
	    else{ // We are on the final channel, so skip back to the beginning and cycle through all channels again to verify correct states
		startSequence((byte)0);
	    }
	    break;
	case 3: // Stage 4, we will be receiving the first synchronisation packet of the sequence to ascertain the n value
	    if (Time.currentTicks() - sink.NCalcFireTime > Time.toTickSpan(Time.SECONDS, 1)){ // If the current time is significantly in advance of the time we were supposed to be called then we know that our nCalc alarm was interrupted, so we restart the sequence on this channel 
		Logger.appendString(csr.s2b("Re-starting on Channel")); // Debug printing
		Logger.appendByte(channel);
		Logger.flush(Mote.WARN);
		sink.state = 0; // Reset the state on this sink
		return 0;
	    }
	    Logger.appendString(csr.s2b("NCalc on channel: ")); // Debug printing
	    Logger.appendByte(channel);
	    Logger.appendString(csr.s2b(" n is: "));
	    Logger.appendByte(data[11]);
	    Logger.flush(Mote.WARN);
	    sink.n = data[DATA_INDEX]; // Set the n value for this channel
	    setTimer(channel, (sink.t * sink.n) + Time.toTickSpan(Time.MILLISECS, TIME_OFFSET)); // Set an alarm to wake the mote at the next required time
	    sink.state = 4;  // Set the sink state to the next stage
	    setChannel(returnChannel); // We need to return to listening on the channel we were before interrupting 
	    break;
	case 4: // Final stage, we no longer need to receive on this channel so we cycle to the next channel
	    if (channel < 2){ // Next sequential channel
		startSequence((byte)(channel+1));
	    }
	    else{
		startSequence((byte)0); // We are on the final channel, so skip back to the beginning and cycle through all channels again to verify correct states
	    }
	}
	return 0;
    }

    private static void handleAlarmCallaback(byte param, long time){
	// Method to handle alarm callbacks
	setChannel(param); // Set the radio channel to the param byte given by the alarm callback
	SinkData sink = sinkStore[param]; // Retrieve the corresponding sink data for the sink
	if (sink.state != 3){ // If we are on a transmit phase move onto transmitting
	    handleTX(param, time);
	}
	else{ // We are on an nCalc stage, so we need to start listening to receive the initial synchronisation pulse
	    radio.startRx(Device.ASAP | Device.RX4EVER, 0, 0);
	}
    }

    private static void handleTX(byte param, long time){
	// Method to handle transmitting to a sink
	SinkData sink = sinkStore[param]; // Retrieve the relevant sinkData object
	transmitByte = new byte[16]; // Initialise a packet to be transmitted
	transmitByte[0] = Radio.FCF_BEACON; // Set the FCF byte
	transmitByte[1] = Radio.FCA_SRC_SADDR | Radio.FCA_DST_SADDR; // Set the Short Addresses
	Util.set16le(transmitByte, 3, PAN); // PAN to be transmitted on
	Util.set16le(transmitByte, 5, 0xFFFF); // Use the broadcast address
	Util.set16le(transmitByte, 7, PAN); // Own PAN
	Util.set16le(transmitByte, 9, SHORT_ADDR); // Own Short Address
	transmitByte[11] = param; // Data to be send in the packet

	if (radio.getState() == Device.S_RXEN){ // If the radio is currently receiving we need to stop receiving before transmitting
	    radio.stopRx();
	}
	if (LED.getState((byte)0) == 1) // Toggle an LED to indicate transmission
	    LED.setState((byte)0, (byte)0);
	else
	    LED.setState((byte)0, (byte)1);
	
	Logger.appendString(csr.s2b("Transmitting on channel: ")); // Debug printing
	Logger.appendByte(param);
	Logger.flush(Mote.WARN);
	radio.transmit(Device.ASAP|Radio.TXMODE_CCA, transmitByte, 0, 16, 0); // Transmit the packet

	if (sink.state == 2){ // If this is the first transmit on the channel, advance to the nCalc stage
	    setTimer(param,(sink.t * 10)); // Set an alarm to wake the mote at the next required time
	    sink.NCalcFireTime = time + (sink.t * 10); // Store the time to the sink object so we can use it to check if a collision has occurred
	    sink.state = 3; // Set the sink state to the next stage
	}
	else if (sink.state == 4){ // If this is the second or more transmit then we already know n and t so we can simply set an alarm for the next reception period of the sink
	    setTimer(param, (sink.t * 11) + (sink.t * sink.n) + Time.toTickSpan(Time.MILLISECS, TIME_OFFSET)); // Set an alarm to wake the mote at the next required time
	}
	setChannel(returnChannel); // We need to return to listening on the channel we were before interrupting 
    }

    private static void setTimer(byte channel, long time){
	// Method to handle setting alarm times
	Logger.appendString(csr.s2b("Setting Timer on channel: ")); // Debug printing
	Logger.appendByte(channel);
	Logger.flush(Mote.WARN);
	switch(channel){ // Determine which alarm to set depending on channel
	case 0x11: // For channel 0
	case 0:
	    sink0Tmr.setAlarmBySpan(time);
	    break;
	case 0x12: // For channel 1
	case 1:
	    sink1Tmr.setAlarmBySpan(time);
	    break;
	case 0x13: // For channel 2
	case 2:
	    sink2Tmr.setAlarmBySpan(time);
	    break;
	}
    }
    private static void setChannel(byte channel){
	// Method to handle changing the radio to a new channel
	Logger.appendString(csr.s2b("Setting Channel: ")); // Debug printing
	Logger.appendByte(channel);
	Logger.flush(Mote.WARN);
	if (radio.getState() == Device.S_RXEN){ // If the radio is currently listening we need to stop before changing the channel
	    radio.stopRx();
	}
	radio.setState(Device.S_OFF); // Force the radio into an off state
	switch(channel){ // Set the PAN and Short address that corresponds to the wanted channel so that transmissions can be received properly
	case 0:
	    PAN = 0x11;
	    SHORT_ADDR = 0x11;
	    break;
	case 1:
	    PAN = 0x12;
	    SHORT_ADDR = 0x12;
	    break;
	case 2:
	    PAN = 0x13;
	    SHORT_ADDR = 0x12;
	    break;
	}
	radio.setChannel(channel);
	radio.setPanId(PAN, true);
	if (channel <= 2){
	    radio.startRx(Device.ASAP | Device.RX4EVER, 0, 0);
	}
	else{
	    // In this state we have completed all listening required so the mote could be put into a sleep state instead of receiving and only wake up for alarmed transmissions
	}
    }
} 