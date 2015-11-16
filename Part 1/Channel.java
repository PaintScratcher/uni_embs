package openCode;

import ptolemy.actor.util.Time;


public class Channel {
    
    public enum states{
	FIRSTRX, SECONDRX, FIRSTTX, SECONDTX
    }
    
    public int n;
    public Time t;
    public Time nextFireTime;
    public states state = states.FIRSTRX;
}
