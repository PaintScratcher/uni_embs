package openCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

import openCode.Channel.states;
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

    // Ports
    protected TypedIOPort input;
    protected TypedIOPort output;
    protected TypedIOPort channelOutput;
    
    private int currentChannel; // The number of the channel the radio is currently tuned to
    private Integer nextChannel; // The number of the channel the radio will next be tuned to
    private Channel nCalcChannel;
    private Boolean changeChan = false; // Stores if we need to change the channel on the next fire
    private Time waitTime; // Stores the time between when the radio was tuned to the current channel and when the actor is next fired
    
    // Data structures for 
    private ArrayList<Channel> channelStore; // Stack for storing information on channels
    
    public SourceNew(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException  {
	super(container, name);
	input = new TypedIOPort(this, "input", true, false); // Wireless tokens are received on this port
	output = new TypedIOPort(this, "output", false, true); // Wireless tokens are sent via this port
	channelOutput = new TypedIOPort(this, "channelOutput", false, true); // Changes to the wireless channel are sent via this port
    }

    public void initialize() throws IllegalActionException { // Runs when the simulation is started, create all the required data structures
	channelStore = new ArrayList<Channel>(); // Stores channel information
	Channel initialChannel = new Channel();
	initialChannel.channel = 11;
	channelStore.add(initialChannel);
	setChannel(initialChannel.channel); // Set the initial channel to listen on
    }
    
    public void fire() throws IllegalActionException{
	Time currentTime = getDirector().getModelTime(); // Get the model time to be used for various calculations
	if (nextChannel != null){ // Check if we need to change the channel in this fire period
	    setChannel(nextChannel); // Change to the next required channel
	    nextChannel = null; // Return to normal operation upon the next fire
	    if (input.hasToken(0)){ // If this fire was initiated by a token on the input, consume it to avoid loops
		input.get(0);
	    }
	    return;
	}
	Channel channel;
	if (nCalcChannel != null){
	    channel = nCalcChannel;
	}
	else{
	    channel = findChannel(currentTime);
	    if (channel == null){
		channel = channelStore.get(0); // Retrieve the information for the current channel
	    }
	}	
	if (input.hasToken(0)){ // Wireless token has been received
	    switch(channel.state){ // Determine what stage of the system we are at
	    case FIRSTRX: // Stage one, we will be receiving the first synchronisation packet
		handleFirstRX(channel, currentTime);
		break;
	    case SECONDRX: // Stage two, we will be receiving the second synchronisation packet
		handleSecondRX(channel, currentTime);
		break;
	    case NCALC: // Stage 4, we will be receiving the first synchronisation packet of the sequence to ascertain the n value
		handleNCalc(currentTime);
		break;
	    default: // We are in a state that does not require an input token, so we discard it
		input.get(0);
		break;
	    }
	}
	else{ // No token has been received so it is a fire initiated by the source and not a token on the input
	    switch(channel.state){ // Determine what stage of the system we are at
	    case FIRSTTX: // Stage 3, we are in the receive period for the channels sink so we can transmit a packet
		handleFirstTX(channel, currentTime);
		break;
	    case SECONDTX: // Stage 5, we are in the receive period for the second time, so we can transmit a packet
		handleSecondTX(channel, currentTime);
		break;
	    }
	}
    }
    
    private void handleFirstRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	// Method to handle the first stage, receiving the first synchronisation pulse
	IntToken token = (IntToken)input.get(0); // Retrieve the token from the input
	Time timeDelta = currentTime.subtract(waitTime); // Calculate a delta between the current time and when the channel was changed, to find the waiting time
	if (timeDelta.getDoubleValue() <= 1.5 && token.equals(1)){ // Token is 1, and we have been waiting for longer than 1.5s. So we will not have a follow-up token, so can't be used for determining t
	    return;
	}
	channel.t = currentTime;
	channel.state = states.SECONDRX; // Set the channel state to the next stage
	channel.firstValue = token.intValue(); // Store the value we have received
	System.out.println("FIRSTRX on channel " + currentChannel + channel.channel + " currentTime is " + currentTime);
    }
    
    private void handleSecondRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
   	// Method to handle the second stage, receiving the second synchronisation pulse
   	int currentValue = ((IntToken) input.get(0)).intValue(); // Retrieve the value from the input
   	channel.t = currentTime.subtract(channel.t);
   	if (channel.firstValue == 1 && currentValue == 1){ // If the first two values are 1 then n is 1
   	    channel.n = 1;
   	    channel.t = new Time(getDirector()).add(channel.t.getDoubleValue() / 12); // Calculate t given that n is 1, i.e divide the period by 12 as per the protocol
   	}
   	setNextFireTime(channel, currentTime.getDoubleValue() + (channel.t.getDoubleValue() * currentValue)); // Manually set the next fire time for this channel
   	channel.state = states.FIRSTTX; // Set the channel state to the next stage
   	System.out.println("SECONDRX on channel " + currentChannel + channel.channel + ". Current value is " + currentValue + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
   	nextChannel(channel, currentTime);
       }
    
    private void handleFirstTX(Channel channel, Time currentTime) throws NoRoomException, IllegalActionException{
	// Method to handle the third stage, transmitting the first packet to a sink
	IntToken token = new IntToken(currentChannel); // Create a token to send
	output.send(0, token); // Send the token
	if (channel.n != null){ // If we have already calculated n in a previous stage (if n is 1) we can skip straight to the next transmission period
	    setNextFireTime(channel, currentTime.getDoubleValue() + (channel.t.getDoubleValue() * 12));
	    channel.state = states.SECONDTX;
	}
	else{ // If we do not know n then we need to next fire at the beginning of the next synchronisation phase so we can receive the first pulse and find n
	    setNextFireTime(channel, currentTime.getDoubleValue() + (channel.t.getDoubleValue() * 11)-0.02);
	    channel.state = states.NCALC; 
	}
	System.out.println("FIRSTTX on channel " + currentChannel + channel.channel + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
	returnLastChannel(currentTime);
     }
    
    private void handleNCalc(Time currentTime) throws NoTokenException, IllegalActionException{
	// Method to handle the fourth stage, where we listen to the first synchronisation pulse to find the value of n
	Channel channel = nCalcChannel;
	channel.n = ((IntToken) input.get(0)).intValue();
	setNextFireTime(channel, currentTime.getDoubleValue() + (channel.t.getDoubleValue() * channel.n));
	channel.state = states.SECONDTX;
	System.out.println("NCALC on channel " + currentChannel + channel.channel + " n is: " + channel.n + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
	nCalcChannel = null;
	returnLastChannel(currentTime);
    }
    
    private void handleSecondTX(Channel channel, Time currentTime) throws IllegalActionException{
	IntToken token = new IntToken(currentChannel);
	output.send(0, token);
	channel.state = states.FINISHED;
	System.out.println("SECONDTX on channel " + currentChannel + ". t is " + channel.t + " currentTime is " + currentTime);
	returnLastChannel(currentTime);
    }
    
    private void setNextFireTime(Channel channel, double additionalTime) throws IllegalActionException{
 	channel.nextFireTime = new Time(getDirector()).add(additionalTime + (currentChannel / 100.0));
 	getDirector().fireAt(this, channel.nextFireTime);
     }

    private void setChannel(int channel) throws IllegalActionException{
	waitTime = getDirector().getModelTime();
	System.out.println("SETTING CHANNEL: " + channel + " at " + waitTime);
	channelOutput.setTypeEquals(BaseType.INT);
	channelOutput.send(0, new IntToken(channel));
	currentChannel = channel;
    }

    public void returnLastChannel(Time currentTime) throws IllegalActionException{
	nextChannel = channelStore.get(0).channel;
	getDirector().fireAt(this, currentTime.add(0.0000001));
    }

    public Channel findChannel(Time currentTime) throws IllegalActionException{
	for (Channel channel: channelStore){ // We need to find the channel that has caused the actor to be fired in this time period
	    if (channel.nextFireTime != null && channel.state != states.FINISHED){ // If we have a valid firing time, that has been initialised
		if (!channel.nextFireTime.equals(currentTime)){ // We are on an incorrect channel, continue searching
		    continue;
		}
		else{ // We have found the correct channel
		    if (currentChannel == channel.channel){  // If we are on the channel that we desire
			if (channel.state == states.NCALC){
			    nCalcChannel = channel;
			}
			System.out.println("Found channel: " + channel.channel);
			return channel;
		    }
		    else { // We are not on the desired channel, so we have to change to it
			//				System.out.println(currentChannel + " : " + desiredChannelNum);
			setChannel(channel.channel); // Set the channel to the desired channel
			channel.nextFireTime = currentTime.add(0.0000001); // Set the channels fire time to when we are firing it so we can detect which channel fired
			getDirector().fireAt(this, currentTime.add(0.0000001)); // Fire the actor again so we can process the transmit
		    }   
		}
	    }
	}
	return null;
    }

    public void nextChannel(Channel currentChannel, Time currentTime) throws IllegalActionException{
	Channel newChannel = new Channel();
	newChannel.channel = currentChannel.channel + 1;
	channelStore.add(0,newChannel);
	nextChannel = newChannel.channel;
	getDirector().fireAt(this, currentTime.add(0.0000001));
    }
}
