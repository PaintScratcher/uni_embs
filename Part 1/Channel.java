package openCode;

import ptolemy.actor.util.Time;


public class Channel {
    
    public enum states{
	FIRSTRX, SECONDRX, FIRSTTX, NCALC, SECONDTX
    }
    
    public Integer n;
    public Time t;
    public Time nextFireTime;
    public states state = states.FIRSTRX;
    public int firstValue;
    public Boolean secondRun = false;
}
