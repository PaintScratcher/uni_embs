package openCode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

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
		
		channelStore = new HashMap(); // Stores channel information
		channelQueue = new LinkedList<Integer>(Arrays.asList(11, 12, 13, 14, 15)); // Keeps track of which channels are left to send to
		
		setChannel(channelQueue.peek());
	}

	public void fire() throws IllegalActionException{
	    if (input.hasToken(0)){ // Wireless token has been received 
		Time currentTime = getDirector().getModelTime();
		
		if (channelStore.containsKey(currentChannel)){ // Stage 2
		    Channel channel = channelStore.get(currentChannel);
		    if (channel.nextFireTime != null){ // Stage 3
			// Do the thing
			System.out.println("Stage 3 on channel " + currentChannel);
		    }
		    else{
        		Time firstTime = channel.t;
        		channel.t = currentTime.subtract(firstTime);
        		IntToken token = (IntToken) input.get(0);
        		int currentValue = token.intValue();
        		channel.nextFireTime = new Time(getDirector());
        		channel.nextFireTime = channel.nextFireTime.add((channel.t.getDoubleValue() * currentValue));
        		System.out.println("Stage 2 on channel " + currentChannel + channel.nextFireTime);
		    }
		}
		else{ // Stage 1 - First frame received on this channel
		    Token token = input.get(0);
		    if (token.equals(1)){ // If token is 1, so can't be used for determining t
			channelQueue.remove(currentChannel);
			channelQueue.add(currentChannel);
			return;
		    }
		    
		    Channel channel = new Channel();
		    channel.t = currentTime;
		    channelStore.put(currentChannel, channel);
		    System.out.println("Stage 1 on channel " + currentChannel);
		}	
	    }	
	}
	
	private void setChannel(int channel) throws IllegalActionException{
	    Token channelToken = new IntToken(channel);
	    channelOutput.setTypeEquals(BaseType.INT);
	    channelOutput.send(0, channelToken);
	    currentChannel = channel;
	}
}