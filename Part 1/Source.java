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
	
	private int currentChannel;
	private int nextChannel = 11;
	private Boolean changeChan = false;
	private Time waitTime;
	
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
	    else{ // No token has been received so it is a manual fire
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
		    if(currentChannel == 13){
			System.out.println("...");
		    }
		    System.out.println("COMPARE" + channel.nextFireTime.compareTo(currentTime));
		    System.out.println(currentTime.getDoubleValue());
		    System.out.println(channel.nextFireTime.getDoubleValue());
		    if(channel.nextFireTime.getDoubleValue() >= currentTime.getDoubleValue()){
			switch(channel.state){ // Main logic, determine what stage of the system we are at
			    case FIRSTTX:
				handleFirstTX(channel, currentTime);
				changeChan = true;
				getDirector().fireAt(this, currentTime.add(0.000001));
				break;
			    case NCALC:
				break;
			    case SECONDTX:
				handleSecondTX(channel, currentTime);
				changeChan = true;
				getDirector().fireAt(this, currentTime.add(0.000001));
				break;
			    }
		    }
		}
		else{
		    if (desiredChannelNum == 0){
			System.out.println("Time at desiredChannelNum" + currentTime);
		    }
		    if (channelQueue.size() != 1){
			nextChannel = currentChannel;
			setChannel(desiredChannelNum);
			channel.nextFireTime = currentTime.add(0.0000001);
			getDirector().fireAt(this, currentTime.add(0.0000001));
		    }
		}   
	    }
	}
	
	private void handleFirstRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	    IntToken token = (IntToken)input.get(0);
	    Time timeDelta = currentTime.subtract(waitTime);
	    System.out.println(timeDelta + " chan " +  currentChannel);
	    if (timeDelta.getDoubleValue() >= 1.5 && token.equals(1)){ // Token is 1, so we will not have a follow-up token, so can't be used for determining t
		channelQueue.remove();
		channelQueue.add(currentChannel);
		return;
	    }
	    if (!channel.secondRun){
		channel.t = currentTime;
	    }
    	    channel.state = states.SECONDRX;
    	    channel.firstValue = token.intValue();
    	    System.out.println("FIRSTRX on channel " + currentChannel + " currentTime is " + currentTime);
	}
	
	private void handleSecondRX(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	    if (!channel.secondRun){
		channel.t = currentTime.subtract(channel.t);
	    }
    	    int currentValue = ((IntToken) input.get(0)).intValue();
    	    if (channel.firstValue == 1 && currentValue == 1){
    		channel.n = 1;
    		channel.t = new Time(getDirector()).add(channel.t.getDoubleValue() / 12);
    	    }
    	    setNextFireTime(channel, currentTime.getDoubleValue() + (channel.t.getDoubleValue() * currentValue));
    	    channel.state = states.FIRSTTX;
    	    System.out.println("SECONDRX on channel " + currentChannel + ". Current value is " + currentValue + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
    	    nextChannel(currentChannel, currentTime);
	}
	
	private void handleFirstTX(Channel channel, Time currentTime) throws NoRoomException, IllegalActionException{
	    IntToken token = new IntToken(currentChannel);
	    output.send(0, token);
	    if (channel.n != null){
		setNextFireTime(channel, currentTime.getDoubleValue() + (channel.t.getDoubleValue() * 12));
		channel.state = states.SECONDTX;  
	    }
	    else{
		setNextFireTime(channel, currentTime.getDoubleValue() + (channel.t.getDoubleValue() * 11)-0.02);
		channel.state = states.NCALC; 
	    }
	    System.out.println("FIRSTTX on channel " + currentChannel + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
	}

	private void handleNCalc(Channel channel, Time currentTime) throws NoTokenException, IllegalActionException{
	    if (channel.secondRun){
		removeFromQueue(currentChannel);
	    }
	    if(currentTime.subtract(channel.nextFireTime).getDoubleValue() > 2){
		channel.secondRun = true;
		channel.state = states.FIRSTRX;
		System.out.println("NCALC on channel " + currentChannel + " n is: " + channel.n + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
		nextChannel(currentChannel, currentTime);
	    }
	    else{
		channel.n = ((IntToken) input.get(0)).intValue();
		setNextFireTime(channel, currentTime.getDoubleValue() + (channel.t.getDoubleValue() * channel.n));
		channel.state = states.SECONDTX;
		System.out.println("NCALC on channel " + currentChannel + " n is: " + channel.n + ". t is " + channel.t + ". nextFireTime is " + channel.nextFireTime + " currentTime is " + currentTime);
		nextChannel(currentChannel, currentTime);
	    }
	}
	
	private void handleSecondTX(Channel channel, Time currentTime) throws IllegalActionException{
	    IntToken token = new IntToken(currentChannel);
	    output.send(0, token);
	    removeFromQueue(currentChannel);
	    System.out.println("SECONDTX on channel " + currentChannel + ". t is " + channel.t + " currentTime is " + currentTime);
	    System.out.println(channelQueue.toString());
	}
	
	private void setChannel(int channel) throws IllegalActionException{
//	    if (channel == 0){
//		System.out.println("0 CHANNEL");
//		return;
//	    }
	    waitTime = getDirector().getModelTime();
	    System.out.println("SETTING CHANNEL: " + channel + " at " + waitTime);
	    channelOutput.setTypeEquals(BaseType.INT);
	    channelOutput.send(0, new IntToken(channel));
	    currentChannel = channel;
	}
	
	private void nextChannel(int currentChannel, Time currentTime) throws IllegalActionException{
	    if (channelQueue.size() == 1){
		System.out.println("SIZE = 1");
		return;
	    }
	    System.out.println("NEXT CHANNEL at" + currentTime);
	    removeFromQueue(currentChannel);
	    channelQueue.add(currentChannel);
	    nextChannel = channelQueue.peek();
	    changeChan = true;
	    getDirector().fireAt(this, currentTime.add(0.0000001));
	}
	
	private void setNextFireTime(Channel channel, double additionalTime) throws IllegalActionException{
	    System.out.println("setNextFireTime");
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