package openCode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

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

public class Source extends TypedAtomicActor {

    // Ports
    protected TypedIOPort input;
    protected TypedIOPort output;
    protected TypedIOPort channelOutput;

    private int currentChannel; // The number of the channel the radio is currently tuned to
    private int nextChannel; // The number of the channel the radio will next be tuned to
    private Boolean changeChan = false; // Stores if we need to change the channel on the next fire
    private Time waitTime; // Stores the time between when the radio was tuned to the current channel and when the actor is next fired

    // Data structures for 
    private Queue<Integer> channelQueue; // Queue for initially scanning through channels
    private HashMap<Integer, Channel> channelStore; // Stores the channel information 

    public Source(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException  {
	super(container, name);
	input = new TypedIOPort(this, "input", true, false); // Wireless tokens are received on this port
	output = new TypedIOPort(this, "output", false, true); // Wireless tokens are sent via this port
	channelOutput = new TypedIOPort(this, "channelOutput", false, true); // Changes to the wireless channel are sent via this port
    }

    public void initialize() throws IllegalActionException { // Runs when the simulation is started, create all the required data structures
	channelStore = new HashMap<Integer, Channel>(); // Stores channel information
	channelQueue = new LinkedList<Integer>(Arrays.asList(11, 12, 13, 14, 15)); // Keeps track of which channels are left to send to

	for (int channelNum : channelQueue){ // Initialise the channel store by creating a channel object for each element in channelQueue
	    Channel channel = new Channel();
	    channelStore.put(channelNum, channel);
	}
	setChannel(channelQueue.peek()); // Set the initial channel to listen on
    }

    public void fire() throws IllegalActionException{
	Time currentTime = getDirector().getModelTime(); // Get the model time to be used for various calculations

	if (changeChan){ // Check if we need to change the channel in this fire period
	    setChannel(nextChannel); // Change to the next required channel
	    changeChan = false; // Return to normal operation upon the next fire
	    if (input.hasToken(0)){ // If this fire was initiated by a token on the input, consume it to avoid loops
		input.get(0);
	    }
	}
	else if (input.hasToken(0)){ // Wireless token has been received f
	    Channel channel = channelStore.get(currentChannel); // Retrieve the information for the current channel
	    switch(channel.state){ // Determine what stage of the system we are at
	    case FIRSTRX: // Stage one, we will be receiving the first synchronisation packet
		handleFirstRX(channel, currentTime);
		break;
	    case SECONDRX: // Stage two, we will be receiving the second synchronisation packet
		handleSecondRX(channel, currentTime);
		break;
	    case NCALC: // Stage 4, we will be receiving the first synchronisation packet of the sequence to ascertain the n value
		handleNCalc(channel, currentTime);
		break;
	    default: // We are in a state that does not require an input token, so we discard it
		input.get(0);
		break;
	    }
	}
	else{ // No token has been received so it is a fire initiated by the source and not a token on the input
	    Channel channel = null; // Initialise a channel to be used later
	    int desiredChannelNum = currentChannel; // Initialise a variable to store the channel required for this firing 
	    for (int channelNum: channelStore.keySet()){ // We need to find the channel that has caused the actor to be fired in this time period
		channel = channelStore.get(channelNum); // Retrieve the information for the channel being checked
		if (channel.nextFireTime != null && channel.state != states.FINISHED){ // If we have a valid firing time, that has been initialised
		    if (!channel.nextFireTime.equals(currentTime)){ // We are on an incorrect channel, continue searching
			continue;
		    }
		    else{ // We have found the correct channel
			desiredChannelNum = channelNum; // Set the desired channel to the channel we have found
			break;
		    }
		}
	    }
	    if (currentChannel == desiredChannelNum){  // If we are on the channel that we desire
		System.out.println(channel.nextFireTime.getDoubleValue() <= currentTime.getDoubleValue() + 0.4);
		if(channel.nextFireTime.getDoubleValue() <= currentTime.getDoubleValue() + 0.4){ // If we are not too early for the firing time
		    switch(channel.state){ // Determine what stage of the system we are at
		    case FIRSTTX: // Stage 3, we are in the receive period for the channels sink so we can transmit a packet
			handleFirstTX(channel, currentTime);
			changeChan = true; // Change back to the channel back to what we had previously
			getDirector().fireAt(this, currentTime.add(0.000001));
			break;
		    case SECONDTX: // Stage 5, we are in the receive period for the second time, so we can transmit a packet
			handleSecondTX(channel, currentTime);
			changeChan = true; // Change back to the channel back to what we had previously
			getDirector().fireAt(this, currentTime.add(0.000001));
			break;
		    }
		}
	    }
	    else if (desiredChannelNum != 0){ // We are not on the desired channel, so we have to change to it
//		System.out.println(currentChannel + " : " + desiredChannelNum);
		nextChannel = currentChannel; // Set the channel we will change back to after the transmit to our current channel
		setChannel(desiredChannelNum); // Set the channel to the desired channel
		channel.nextFireTime = currentTime.add(0.0000001); // Set the channels fire time to when we are firing it so we can detect which channel fired
		getDirector().fireAt(this, currentTime.add(0.0000001)); // Fire the actor again so we can process the transmit
	    }   
	}
    }

    private void handleFirstRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	// Method to handle the first stage, receiving the first synchronisation pulse
	IntToken token = (IntToken)input.get(0); // Retrieve the token from the input
	Time timeDelta = currentTime.subtract(waitTime); // Calculate a delta between the current time and when the channel was changed, to find the waiting time
	if (timeDelta.getDoubleValue() <= 1.5 && token.equals(1)){ // Token is 1, and we have been waiting for longer than 1.5s. So we will not have a follow-up token, so can't be used for determining t
	    channelQueue.remove(); // Send the current channel to the back of the queue so we can do something useful while it is in its sleep state
	    channelQueue.add(currentChannel);
	    return;
	}
	if (!channel.secondRun){ // If this is the first time we have received packets on the channel we will need the current time to calculate a t
	    channel.t = currentTime;
	}
	channel.state = states.SECONDRX; // Set the channel state to the next stage
	channel.firstValue = token.intValue(); // Store the value we have received
	System.out.println("FIRSTRX on channel " + currentChannel + " currentTime is " + currentTime);
    }

    private void handleSecondRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	// Method to handle the second stage, receiving the second synchronisation pulse
	if (!channel.secondRun){ // If this is the second time we have received packets on the channel we already have t so no need to calculate it
	    channel.t = currentTime.subtract(channel.t);
	}
	int currentValue = ((IntToken) input.get(0)).intValue(); // Retrieve the value from the input
	if (channel.firstValue == 1 && currentValue == 1){ // If the first two values are 1 then n is 1
	    channel.n = 1;
	    channel.t = new Time(getDirector()).add(channel.t.getDoubleValue() / 12); // Calculate t given that n is 1, i.e divide the period by 12 as per the protocol
	}
	setNextFireTime(channel, currentTime.getDoubleValue() + (channel.t.getDoubleValue() * currentValue)); // Manually set the next fire time for this channel
	channel.state = states.FIRSTTX; // Set the channel state to the next stage
	System.out.println("SECONDRX on channel " + currentChannel + ". Current value is " + currentValue + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
	nextChannel(currentChannel, currentTime);
	removeFromQueue(currentChannel); // We can now move onto listening on the next channel
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
	System.out.println("FIRSTTX on channel " + currentChannel + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
    }

    private void handleNCalc(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	// Method to handle the fourth stage, where we listen to the first synchronisation pulse to find the value of n
	System.out.println(channel.nextFireTime.getDoubleValue() >= currentTime.getDoubleValue() + 0.4);
	if(channel.nextFireTime.getDoubleValue() >= currentTime.getDoubleValue()){
	    input.get(0);
	    return;
	}
	else if (channel.secondRun){ // If we are on the second run through, we have already transmitted twice so we can stop here
	    	channel.state = states.FINISHED;
	    return;
	}
	else if (currentTime.subtract(channel.nextFireTime).getDoubleValue() > 2){ // There is a large delta between when we were supposed to fire and when we fired, so we probably have a collision with another channel
	    channel.secondRun = true; // Store that we will need to go around again for this channel
	    channel.state = states.FIRSTRX; // Set the stage back to the beginning so we can restart
	    System.out.println("NCALC on channel " + currentChannel + " n is: " + channel.n + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
	    nextChannel(currentChannel, currentTime);
	}
	else{
	    channel.n = ((IntToken) input.get(0)).intValue();
	    setNextFireTime(channel, currentTime.getDoubleValue() + (channel.t.getDoubleValue() * channel.n));
	    channel.state = states.SECONDTX;
	    System.out.println("NCALC on channel " + currentChannel + " n is: " + channel.n + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
	    nextChannel(currentChannel, currentTime);
	    removeFromQueue(currentChannel);
	}
    }

    private void handleSecondTX(Channel channel, Time currentTime) throws IllegalActionException{
	IntToken token = new IntToken(currentChannel);
	output.send(0, token);
	channel.state = states.FINISHED;
	System.out.println("SECONDTX on channel " + currentChannel + ". t is " + channel.t + " currentTime is " + currentTime);
	System.out.println(channelQueue.toString());
    }

    private void setChannel(int channel) throws IllegalActionException{
	waitTime = getDirector().getModelTime();
	System.out.println("SETTING CHANNEL: " + channel + " at " + waitTime);
	channelOutput.setTypeEquals(BaseType.INT);
	channelOutput.send(0, new IntToken(channel));
	currentChannel = channel;
    }

    private void nextChannel(int currentChannel, Time currentTime) throws IllegalActionException{
	System.out.println("NEXT CHANNEL at" + currentTime);
	removeFromQueue(currentChannel);
	channelQueue.add(currentChannel);
	nextChannel = channelQueue.peek();
	changeChan = true;
	getDirector().fireAt(this, currentTime.add(0.0000001));
    }

    private void setNextFireTime(Channel channel, double additionalTime) throws IllegalActionException{
	channel.nextFireTime = new Time(getDirector()).add(additionalTime + (currentChannel / 100.0));
	getDirector().fireAt(this, channel.nextFireTime);
    }

    private void removeFromQueue(int channel){
	Queue<Integer> tempQueue = new LinkedList<Integer>();
	while(!channelQueue.isEmpty()){
	    int temp = channelQueue.remove();
	    if(temp != channel){
		tempQueue.add(temp);
	    }
	}
	channelQueue = tempQueue;   
    }
}