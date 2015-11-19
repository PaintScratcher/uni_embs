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
    private int nextChannel; // The number of the channel the radio will next be tuned to
    private Boolean changeChan = false; // Stores if we need to change the channel on the next fire
    private Time waitTime; // Stores the time between when the radio was tuned to the current channel and when the actor is next fired
    
    // Data structures for 
    private ArrayList<Channel> channelStore; // Stack for storing information on channels
    private int endOfArray = channelStore.size() - 1;
    
    public SourceNew(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException  {
	super(container, name);
	input = new TypedIOPort(this, "input", true, false); // Wireless tokens are received on this port
	output = new TypedIOPort(this, "output", false, true); // Wireless tokens are sent via this port
	channelOutput = new TypedIOPort(this, "channelOutput", false, true); // Changes to the wireless channel are sent via this port
    }

    public void initialize() throws IllegalActionException { // Runs when the simulation is started, create all the required data structures
	channelStore = new ArrayList<Channel>(); // Stores channel information

	nextChannel();
    }
    public void fire() throws IllegalActionException{
	Time currentTime = getDirector().getModelTime(); // Get the model time to be used for various calculations
	
	if (input.hasToken(0)){ // Wireless token has been received f
	    Channel channel = channelStore.get(channelStore.size() - 1); // Retrieve the information for the current channel
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
    }
    
    private void handleFirstRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	// Method to handle the first stage, receiving the first synchronisation pulse
	IntToken token = (IntToken)input.get(0); // Retrieve the token from the input
	Time timeDelta = currentTime.subtract(waitTime); // Calculate a delta between the current time and when the channel was changed, to find the waiting time
	if (timeDelta.getDoubleValue() <= 1.5 && token.equals(1)){ // Token is 1, and we have been waiting for longer than 1.5s. So we will not have a follow-up token, so can't be used for determining t
	    channelStore.remove(); // Send the current channel to the back of the queue so we can do something useful while it is in its sleep state
	    channelStore.add(currentChannel);
	    return;
	}
	channel.state = states.SECONDRX; // Set the channel state to the next stage
	channel.firstValue = token.intValue(); // Store the value we have received
	System.out.println("FIRSTRX on channel " + currentChannel + " currentTime is " + currentTime);
    }

    private void setChannel(int channel) throws IllegalActionException{
	waitTime = getDirector().getModelTime();
	System.out.println("SETTING CHANNEL: " + channel + " at " + waitTime);
	channelOutput.setTypeEquals(BaseType.INT);
	channelOutput.send(0, new IntToken(channel));
    }

    public void returnLastChannel(){
	return;
    }
    
    public void nextChannel(){
	Channel channel = new Channel();
	channelStore.add(channel);
	return;
    }
}
