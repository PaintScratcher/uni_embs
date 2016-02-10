// Exam Number: Y0070813

package openCode;

// Required Libraries 
import java.util.ArrayList;

import openCode.SinkData.states;
import ptolemy.actor.NoRoomException;
import ptolemy.actor.NoTokenException;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.IntToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class SourceNew extends TypedAtomicActor {

    // Ports for the actor
    protected TypedIOPort input; // Wireless tokens are received on this port
    protected TypedIOPort output; // Wireless tokens are sent via this port
    protected TypedIOPort channelOutput; // Changes to the wireless channel are sent via this port
    
    private Integer currentChannel; // The number of the channel the radio is currently tuned to
    private Integer nextChannel; // The number of the channel the radio will next be tuned to
    private SinkData nCalcSink; // Stores the channel that we need to listen on to find a value for n
    private Time waitTime; // Stores the time between when the radio was tuned to the current channel and when the actor is next fired
    
    // Data structures
    private ArrayList<SinkData> sinkStore; // Array for storing information on sinks
    
    public SourceNew(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException  {
	// Constructor for the actor, run when it is first loaded
	super(container, name);
	input = new TypedIOPort(this, "input", true, false); // Wireless tokens are received on this port
	output = new TypedIOPort(this, "output", false, true); // Wireless tokens are sent via this port
	channelOutput = new TypedIOPort(this, "channelOutput", false, true); // Changes to the wireless channel are sent via this port
	output.setTypeEquals(BaseType.INT); // Set the port output type to int
    }

    public void initialize() throws IllegalActionException {
	// Runs when the simulation is started, create all the required data structures
	// We need to start the protocol by initialising the data structures and starting on the first channel (11)
	sinkStore = new ArrayList<SinkData>(); // Stores channel information
	SinkData initialSink = new SinkData(); // Create the initial sink object
	initialSink.channel = 11; // Set the initial channel to 11
	sinkStore.add(initialSink); // Add the initialised sink to the storage array
	setChannel(initialSink.channel); // Set the radio to the initial channel
    }
    
    public void fire() throws IllegalActionException{
	// Runs whenever the actor is called. Either when a token is received on the input port or at a time which has been manually specified
	// We will determine what stage the protocol is in and what sink we need to take action on, and delegate the handling to a series of handlers
	Time currentTime = getDirector().getModelTime(); // Get the current model time to be used for various calculations
	if (nextChannel != null){ // Check if we need to change the channel in this fire period
	    setChannel(nextChannel); // Change to the next required channel
	    nextChannel = null; // Return to normal operation upon the next fire
	    if (input.hasToken(0)){ // If this fire was initiated by a token on the input, consume it to avoid loops
		input.get(0);
	    }
	    return;
	}
	SinkData sink; // Initialise a sink object so it has the correct scope
	if (nCalcSink != null){ // If we are in the nCalc state, use the channel we have specified 
	    sink = nCalcSink;
	}
	else{ // We need to find the sink which has caused the fire
	    sink = findChannel(currentTime); // Find the sink
	    if (sink == null){ // If we can not find a sink, then it was not a manual fire, it must have been a token on the input. Therefore we need the channel currently being listened to
		sink = sinkStore.get(0); // Retrieve the information for the current sink
	    }
	}	
	if (input.hasToken(0)){ // Wireless token has been received
	    switch(sink.state){ // Determine what stage of the system we are at
	    case FIRSTRX: // Stage one, we will be receiving the first synchronisation packet
		handleFirstRX(sink, currentTime);
		break;
	    case SECONDRX: // Stage two, we will be receiving the second synchronisation packet
		handleSecondRX(sink, currentTime);
		break;
	    case NCALC: // Stage 4, we will be receiving the first synchronisation packet of the sequence to ascertain the n value
		handleNCalc(currentTime);
		break;
	    default: // We are in a state that does not require an input token, so we discard it
		input.get(0);
		break;
	    }
	}
	else{ // No token has been received so it is a fire initiated manually by the actor and not a token on the input
	    switch(sink.state){ // Determine what stage of the system we are at
	    case FIRSTTX: // Stage 3, we are in the receive period for the sink so we can transmit a packet
		handleFirstTX(sink, currentTime);
		break;
	    case SECONDTX: // Stage 5, we are in the receive period for the second time, so we can transmit a packet
		handleSecondTX(sink, currentTime);
		break;
	    }
	}
    }
    
    private void handleFirstRX(SinkData sink, Time currentTime) throws NoTokenException, IllegalActionException{
	// Method to handle the first stage, receiving the first synchronisation pulse
	IntToken token = (IntToken)input.get(0); // Retrieve the token from the input
	Time timeDelta = currentTime.subtract(waitTime); // Calculate a delta between the current time and when the channel was changed, to find the waiting time
	if (timeDelta.getDoubleValue() <= 1.5 && token.equals(1)){ // Token is 1, and we have been waiting for longer than 1.5s. So we will not have a follow-up token, so can't be used for determining t
	    return;
	}
	sink.t = currentTime; // Store the current model time so we can use it for determining t in the next stage
	sink.state = states.SECONDRX; // Set the sink state to the next stage
	sink.firstValue = token.intValue(); // Store the value we have received for determining if n is 1
	System.out.println("FIRSTRX on channel " + currentChannel + sink.channel + " currentTime is " + currentTime); // Informational print
    }
    
    private void handleSecondRX(SinkData sink, Time currentTime) throws NoTokenException, IllegalActionException{
   	// Method to handle the second stage, receiving the second synchronisation pulse
   	int currentValue = ((IntToken) input.get(0)).intValue(); // Retrieve the value from the input
   	sink.t = currentTime.subtract(sink.t); // Calculate the value for t
   	if (sink.firstValue == 1 && currentValue == 1){ // If the first two values are 1 then n is 1
   	    sink.n = 1; // Set the n value to 1
   	    sink.t = new Time(getDirector()).add(sink.t.getDoubleValue() / 12); // Calculate t given that n is 1, i.e divide the period by 12 as per the protocol
   	}
   	setNextFireTime(sink, currentTime.getDoubleValue() + (sink.t.getDoubleValue() * currentValue)); // Manually set the next fire time for this sink
   	sink.state = states.FIRSTTX; // Set the sink state to the next stage
   	System.out.println("SECONDRX on channel " + currentChannel + sink.channel + ". Current value is " + currentValue + ". t is " + sink.t + ". nextFireTime is " + sink.nextFireTime + " currentTime is " + currentTime); // Informational print
   	nextChannel(sink, currentTime); // As we have a value for t (and possibly n) we have no need to listen to this channel for the moment, so we switch to listening on the next channel
       }
    
    private void handleFirstTX(SinkData sink, Time currentTime) throws NoRoomException, IllegalActionException{
	// Method to handle the third stage, transmitting the first packet to a sink
	IntToken token = new IntToken(currentChannel); // Create a token to send, with the value being the channel we are transmitting on
	output.send(0, token); // Send the token
	if (sink.n != null){ // If we have already calculated n in a previous stage (if n is 1) we can skip straight to the next transmission period
	    setNextFireTime(sink, currentTime.getDoubleValue() + (sink.t.getDoubleValue() * 12)); // Manually set the next fire time for this sink
	    sink.state = states.SECONDTX; // Set the sink state to the next stage
	}
	else{ // If we do not know n then we need to next fire at the beginning of the next synchronisation phase so we can receive the first pulse and find n
	    setNextFireTime(sink, currentTime.getDoubleValue() + (sink.t.getDoubleValue() * 11)-0.02); // Manually set the next fire time for this sink
	    sink.state = states.NCALC; // Set the sink state to the next stage
	}
	System.out.println("FIRSTTX on channel " + currentChannel + sink.channel + ". t is " + sink.t + ". nextFireTime is " + sink.nextFireTime + " currentTime is " + currentTime); // Informational print
	returnLastChannel(currentTime); // After transmitting we need to return to listening on the channel we were previously listening on
     }
    
    private void handleNCalc(Time currentTime) throws NoTokenException, IllegalActionException{
	// Method to handle the fourth stage, where we listen to the first synchronisation pulse to find the value of n
	SinkData sink = nCalcSink; // Get the sink information
	sink.n = ((IntToken) input.get(0)).intValue(); // Get the value received in the wireless token and store it as the sinks n value
	setNextFireTime(sink, currentTime.getDoubleValue() + (sink.t.getDoubleValue() * sink.n)); // Manually set the next fire time for this sink
	sink.state = states.SECONDTX; // Set the sink state to the next stage
	System.out.println("NCALC on channel " + currentChannel + sink.channel + " n is: " + sink.n + ". t is " + sink.t + ". nextFireTime is " + sink.nextFireTime + " currentTime is " + currentTime); // Informational print
	nCalcSink = null; // Remove the sink used for this nCalc phase so it does not run again upon the next fire
	returnLastChannel(currentTime); // After finding the n value we need to return to listening on the channel we were previously listening on
    }
    
    private void handleSecondTX(SinkData channel, Time currentTime) throws IllegalActionException{
	// Method to handle the final stage, transmitting the second packet to a sink
	IntToken token = new IntToken(currentChannel); // Create a token to send, with the value being the channel we are transmitting on
	output.send(0, token); // Send the token
	channel.state = states.FINISHED; // Set the channel state to the next stage
	System.out.println("SECONDTX on channel " + currentChannel + ". t is " + channel.t + " currentTime is " + currentTime); // Informational print
	returnLastChannel(currentTime); // After transmitting we need to return to listening on the channel we were previously listening on
    }
    
    private void setNextFireTime(SinkData channel, double additionalTime) throws IllegalActionException{
	// Method to coordinate the time to next fire the actor 
 	channel.nextFireTime = new Time(getDirector()).add(additionalTime + (currentChannel / 100.0)); // Store the fire time to the channel information so we can use it later to determine which channel caused the fire
 	getDirector().fireAt(this, channel.nextFireTime); // Set the actor to fire at the specified time
     }

    private void setChannel(int channel) throws IllegalActionException{
	// Method to handle changing the radio channel
	waitTime = getDirector().getModelTime(); // Store the current model time so we can use it later for determining how long we have been listening for before receiving a token
	System.out.println("SETTING CHANNEL: " + channel + " at " + waitTime); // Informational print
	channelOutput.setTypeEquals(BaseType.INT); // Set the port output type to int
	channelOutput.send(0, new IntToken(channel)); // Send a token to the port, which will change the channel used by the model
	currentChannel = channel; // Set the current channel to be the channel we just switched to
    }

    public void returnLastChannel(Time currentTime) throws IllegalActionException{
	// Method to handle returning to the previous channel after a transmit or nCalc stage
	nextChannel = sinkStore.get(0).channel; // Set a variable which will be picked up when the actor next fires, which will trigger a channel switch to the specified channel
	getDirector().fireAt(this, currentTime.add(0.0000001)); // Fire the actor again very soon to actuate this change
    }

    public SinkData findChannel(Time currentTime) throws IllegalActionException{
	// Method to find the channel which has caused the actor to fire, this will require iterating through all the channels and checking which one has a matching nextFireTime to the current model time
	for (SinkData sink: sinkStore){ // Iterate through all sinks in the sinkStore data structure
	    if (sink.nextFireTime != null && sink.state != states.FINISHED){ // If we have a valid firing time, that has been initialised and the sink has not been finished with
		if (!sink.nextFireTime.equals(currentTime)){ // This is not the sink that caused the fire, continue searching
		    continue;
		}
		else{ // We have found the correct sink
		    if (currentChannel == sink.channel){  // If the radio is listening to the channel we desire
			if (sink.state == states.NCALC){ // If the sink is in the nCalc stage
			    nCalcSink = sink; // Set the nCalcSink variable to the current sink, this will allow the actor to change to the correct channel upon the next fire
			}
			System.out.println("Found channel: " + sink.channel); // Informational print
			return sink; // Return the sink we have found
		    }
		    else { // We are not on the desired channel, so we have to change to it
			setChannel(sink.channel); // Set the channel to the desired channel
			sink.nextFireTime = currentTime.add(0.0000001); // Set the sinks fire time to when we are firing it so we can detect which sink fired
			getDirector().fireAt(this, currentTime.add(0.0000001)); // Fire the actor again so we can process the transmit
		    }  
		}
	    }
	}
	return null;
    }

    public void nextChannel(SinkData currentChannel, Time currentTime) throws IllegalActionException{
	// Method to change the radio to listen to the next channel 
	SinkData newSink = new SinkData(); // Create a new SinkData object
	newSink.channel = currentChannel.channel + 1; // Set the SinkDatas channel to the next sequential channel
	sinkStore.add(0,newSink); // Add the new sinkData to the sinkStore array
	nextChannel = newSink.channel; // Set the nextChannel variable so we initiate a channel switch upon the next actor fire
	getDirector().fireAt(this, currentTime.add(0.0000001)); // Fire the actor again so we can process the switch
    }
}
