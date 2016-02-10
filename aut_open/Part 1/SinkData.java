// Exam Number: Y0070813

package openCode;

import ptolemy.actor.util.Time;

public class SinkData {
    // Class to store information about Sinks 
    
    public enum states{ // An enum to describe the various states that a Sink can be in
	// FirstRX is when we are expecting to receive the first synchronisation pulse
	// SecondRX is when we are expecting to receive the second synchronisation pulse
	// FirstTX is when we are going to transmit for the first time
	// NCalc is when we are listening for the first synchronisation pulse of the sequence to ascertain the n value
	// SecondTX is when we are going to transmit for the second time
	// Finished is when we have completed the sequence and no longer need to transmit or listen to the sink
	FIRSTRX, SECONDRX, FIRSTTX, NCALC, SECONDTX, FINISHED
    }
    
    public Integer channel; // Store the channel associated with the sink
    public Integer n; // Store the sinks n value
    public Time t; // Store the sinks t value
    public Time nextFireTime; // Store the time the sink should next fire at
    public states state = states.FIRSTRX; // Store the state of the sink and default it to the initial state of listening for the first received token
    public int firstValue; // Store the first value received
}
