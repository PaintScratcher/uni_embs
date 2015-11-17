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
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class Source extends TypedAtomicActor {
	
	protected TypedIOPort input;
	protected TypedIOPort output;
	protected TypedIOPort channelOutput;
	
	private int currentChannel;
	private Queue<Integer> channelQueue;
	private HashMap<Integer, Channel> channelStore;
	
	public Source(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException  {

		super(container, name);
		input = new TypedIOPort(this, "input", true, false);
		output = new TypedIOPort(this, "output", false, true);
		channelOutput = new TypedIOPort(this, "channelOutput", false, true);
		
		channelStore = new HashMap<Integer, Channel>(); // Stores channel information
		channelQueue = new LinkedList<Integer>(Arrays.asList(11, 12, 13, 14, 15)); // Keeps track of which channels are left to send to
		
		setChannel(channelQueue.peek());
	}

	public void fire() throws IllegalActionException{
	    Time currentTime = getDirector().getModelTime();
	    if (!input.hasToken(0)){ // Wireless token has not been received - manual fire		
		for (int channelNum : channelQueue){ // For each channel still in channelQueue
		    Channel channel = channelStore.get(channelNum); 
		    if (!channel.nextFireTime.equals(currentTime)){ // We are on an incorrect channel
			continue;
		    }
		    else{ // We have the correct channel
			setChannel(channelNum);
			break;
		    }
		}
	    }

	    if (!channelStore.containsKey(currentChannel)){ // Initial frame on channel, so create a channel in channelStore
		Channel channel = new Channel();
		channelStore.put(currentChannel, channel);
	    }
	    Channel channel = channelStore.get(currentChannel);
	    switch(channelStore.get(currentChannel).state){ // Main logic, determine what stage of the system we are at
	    case FIRSTRX:
		handleFirstRX(channel, currentTime);
		break;
	    case SECONDRX:
		handleSecondRX(channel, currentTime);
		break;
	    case FIRSTTX:
		handleFirstTX(channel, currentTime);
		break;
	    case NCALC:
		handleNCalc(channel, currentTime);
		break;
	    case SECONDTX:
		handleSecondTX(channel);
		break;
	    }

	}
	
	private void handleFirstRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	    Token token = input.get(0);
	    if (token.equals(1)){ // Token is 1, so we will not have a follow-up token, so can't be used for determining t
		channelQueue.remove(currentChannel);
		channelQueue.add(currentChannel);
		return;
	    }
	    
    	    channel.t = currentTime;
    	    channel.state = states.SECONDRX;
    	    System.out.println("FIRSTRX on channel " + currentChannel);
	}
	
	private void handleSecondRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	    Time firstTime = channel.t;
    	    channel.t = currentTime.subtract(firstTime);
    	    IntToken token = (IntToken) input.get(0);
    	    int currentValue = token.intValue();
    	    channel.nextFireTime = new Time(getDirector()).add(currentTime.getDoubleValue() + (channel.t.getDoubleValue() * currentValue));
    	    System.out.println("SECONDRX on channel " + currentChannel + ". Current value is " + currentValue + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
    	    getDirector().fireAt(this, channel.nextFireTime);
    	    channel.state = states.FIRSTTX;    
	}
	
	private void handleFirstTX(Channel channel, Time currentTime) throws NoRoomException, IllegalActionException{
	    
	    if (input.hasToken(0)){ // There is a token on the input that we do not need
		input.get(0);
		return;
	    }
	    System.out.println("FIRSTTX on channel " + currentChannel + " currentTime is " + currentTime);
	    IntToken token = new IntToken(1);
	    output.send(0, token);
	    channel.state = states.NCALC;   
	    setChannel(channelQueue.peek());
	}
	
	private void handleNCalc(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	    System.out.println("NCALC on channel " + currentChannel + " currentTime is " + currentTime);
	    if (input.hasToken(0)){ // There is a token on the input that we do not need
		input.get(0);
		return;
	    }
	    channel.state = states.SECONDTX;
	    
	}
	
	private void handleSecondTX(Channel channel) throws IllegalActionException{
	    System.out.println("SECONDTX on channel " + currentChannel);
	    if (input.hasToken(0)){ // There is a token on the input that we do not need
		input.get(0);
		return;
	    }
	}
	
	private void setChannel(int channel) throws IllegalActionException{
	    Token channelToken = new IntToken(channel);
	    channelOutput.setTypeEquals(BaseType.INT);
	    channelOutput.send(0, channelToken);
	    currentChannel = channel;
	}
}