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
	private int nextChannel = 11;
	private Boolean changeChan = false;
	private Time waitTime;
	private Queue<Integer> channelQueue;
	private HashMap<Integer, Channel> channelStore;
	
	public Source(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException  {

		super(container, name);
		input = new TypedIOPort(this, "input", true, false);
		output = new TypedIOPort(this, "output", false, true);
		channelOutput = new TypedIOPort(this, "channelOutput", false, true);
	}
	
	public void initialize() throws IllegalActionException {
	    channelStore = new HashMap<Integer, Channel>(); // Stores channel information
	    channelQueue = new LinkedList<Integer>(Arrays.asList(11, 12, 13, 14, 15)); // Keeps track of which channels are left to send to

	    for (int channelNum : channelQueue){
		Channel channel = new Channel();
		channelStore.put(channelNum, channel);
	    }
	    setChannel(channelQueue.peek());
	}
	
	public void fire() throws IllegalActionException{
	    Time currentTime = getDirector().getModelTime();
	    
	    if (changeChan){
		setChannel(nextChannel);
		changeChan = false;
		if (input.hasToken(0)){
		    input.get(0);
		}
	    }
	    else if (input.hasToken(0)){ // Wireless token has been received 
		Channel channel = channelStore.get(currentChannel);
		switch(channel.state){ // Main logic, determine what stage of the system we are at
		case FIRSTRX:
		    handleFirstRX(channel, currentTime);
		    break;
		case SECONDRX:
		    handleSecondRX(channel, currentTime);
		    break;
		case NCALC:
		    handleNCalc(channel, currentTime);
		    break;
		default:
		    input.get(0);
		    break;
		 }
	    }
	    else{ // No token has been recieved so it is a manual fire
		Channel channel = null;
		int desiredChannelNum = 0;
		for (int channelNum : channelQueue){ // For each channel still in channelQueue
		    channel = channelStore.get(channelNum);
		    if (channel.nextFireTime != null){
			if (!channel.nextFireTime.equals(currentTime)){ // We are on an incorrect channel
			    continue;
			}
			else{ // We have the correct channel
			    desiredChannelNum = channelNum;
			    break;
			}
		    }
		}
		if (currentChannel == desiredChannelNum){
		    switch(channel.state){ // Main logic, determine what stage of the system we are at
		    case FIRSTTX:
			handleFirstTX(channel, currentTime);
			changeChan = true;
			getDirector().fireAt(this, currentTime.add(0.001));
			break;
		    case NCALC:
			break;
		    case SECONDTX:
			handleSecondTX(channel, currentTime);
			changeChan = true;
			getDirector().fireAt(this, currentTime.add(0.001));
			break;
		    }
		}
		else{
		    nextChannel = currentChannel;
		    setChannel(desiredChannelNum);
		    channel.nextFireTime = currentTime.add(0.001);
		    getDirector().fireAt(this, currentTime.add(0.001));
		}   
	    }
	}
	
	private void handleFirstRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	    Token token = input.get(0);
	    Time timeDelta = waitTime.subtract(currentTime);
	    
	    if (timeDelta.getDoubleValue() > 1.5 && token.equals(1)){ // Token is 1, so we will not have a follow-up token, so can't be used for determining t
		channelQueue.remove();
		channelQueue.add(currentChannel);
		return;
	    }
    	    channel.t = currentTime;
    	    channel.state = states.SECONDRX;
    	    System.out.println("FIRSTRX on channel " + currentChannel + " currentTime is " + currentTime);
	}
	
	private void handleSecondRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
    	    channel.t = currentTime.subtract(channel.t);
    	    int currentValue = ((IntToken) input.get(0)).intValue();
    	    channel.nextFireTime = new Time(getDirector()).add(currentTime.getDoubleValue() + (channel.t.getDoubleValue() * currentValue));
    	    getDirector().fireAt(this, channel.nextFireTime);
    	    channel.state = states.FIRSTTX;
    	    System.out.println("SECONDRX on channel " + currentChannel + ". Current value is " + currentValue + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
    	    nextChannel(currentChannel, currentTime);
	}
	
	private void handleFirstTX(Channel channel, Time currentTime) throws NoRoomException, IllegalActionException{
	    IntToken token = new IntToken(currentChannel);
	    output.send(0, token);
	    channel.nextFireTime = new Time(getDirector()).add(currentTime.getDoubleValue() + (channel.t.getDoubleValue() * 10));
	    getDirector().fireAt(this, channel.nextFireTime);
	    channel.state = states.NCALC;  
	    System.out.println("FIRSTTX on channel " + currentChannel + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
	}

	private void handleNCalc(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	    channel.n = ((IntToken) input.get(0)).intValue();
    	    channel.nextFireTime = new Time(getDirector()).add(currentTime.getDoubleValue() + (channel.t.getDoubleValue() * channel.n));
    	    getDirector().fireAt(this, channel.nextFireTime);
	    channel.state = states.SECONDTX;
	    System.out.println("NCALC on channel " + currentChannel + " n is: " + channel.n + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
	    nextChannel(currentChannel, currentTime);
	}
	
	private void handleSecondTX(Channel channel, Time currentTime) throws IllegalActionException{
	    IntToken token = new IntToken(currentChannel);
	    output.send(0, token);
	    channelQueue.remove(currentChannel);
	    System.out.println("SECONDTX on channel " + currentChannel + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
	    System.out.println(channelQueue.toString());
	}
	
	private void setChannel(int channel) throws IllegalActionException{
	    Time currentTime = getDirector().getModelTime();
	    waitTime = currentTime;
	    System.out.println("SETTING CHANNEL: " + channel + " at " + currentTime);
	    Token channelToken = new IntToken(channel);
	    channelOutput.setTypeEquals(BaseType.INT);
	    channelOutput.send(0, channelToken);
	    currentChannel = channel;
	}
	
	private void nextChannel(int currentChannel, Time currentTime) throws IllegalActionException{
	   // System.out.println("NEXT CHANNEL at " + currentTime);
	    channelQueue.remove();
	    channelQueue.add(currentChannel);
	    nextChannel = channelQueue.peek();
	    changeChan = true;
	    getDirector().fireAt(this, currentTime.add(0.001));
	}
}