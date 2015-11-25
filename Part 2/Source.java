package embs;

import com.ibm.saguaro.logger.*;
import com.ibm.saguaro.system.DevCallback;
import com.ibm.saguaro.system.Device;
import com.ibm.saguaro.system.Mote;
import com.ibm.saguaro.system.Radio;
import com.ibm.saguaro.system.Timer;
import com.ibm.saguaro.system.TimerEvent;
import com.ibm.saguaro.system.Util;
import com.ibm.saguaro.system.csr;

public class Source {
    private static Timer sink0Tmr;
    private static Timer sink1Tmr;
    private static Timer sink2Tmr;   

    private static int DATA_INDEX = 11;
    
    static Radio radio = new Radio();
    
    private static SinkData[] sinkStore = new SinkData[3];
    private static byte[] transmitByte;
    private static byte PAN;
    private static byte SHORT_ADDR;
    private static byte returnChannel;
    
    static {
        for(int i = 0; i < 3; i++){
            sinkStore[i] = new SinkData();
        }
        sink0Tmr = new Timer();
        sink1Tmr = new Timer();
        sink2Tmr = new Timer();
        sink0Tmr.setParam((byte)0);
        sink1Tmr.setParam((byte)1);
        sink2Tmr.setParam((byte)2);
	sink0Tmr.setCallback(new TimerEvent(null){
		public void invoke(byte param, long time) {
		    handleAlarmCallaback(param, time);
		}
	});
	sink1Tmr.setCallback(new TimerEvent(null){
		public void invoke(byte param, long time) {
		    handleAlarmCallaback(param, time);
		}
	});
	sink2Tmr.setCallback(new TimerEvent(null){
		public void invoke(byte param, long time) {
		    handleAlarmCallaback(param, time);
		}
	});       
	
	// Open the default radio
        radio.open(Radio.DID, null, 0, 0);
	radio.setRxHandler(new DevCallback(null){
            public int invoke (int flags, byte[] data, int len, int info, long time) {
                return  Source.handleRX(flags, data, len, info, time);
            }
        });
        startSequence((byte)0);
    }

    private static void startSequence(byte channel){
	setChannel(channel);
	radio.startRx(Device.ASAP | Device.RX4EVER, 0, 0);
    }
    
    private  static int handleRX(int flags, byte[] data, int len, int info, long time){
	if (data == null) {
		return 0;
	}

	byte channel = radio.getChannel();
//	Logger.appendString(csr.s2b("RECIEVED: "));
//	Logger.appendByte(data[11]);
//	Logger.appendString(csr.s2b(" on Channel: "));
//	Logger.appendByte(channel);
//	Logger.flush(Mote.WARN);
	SinkData sink = sinkStore[channel];
	switch(sink.state){
	    case 0:
		Logger.appendString(csr.s2b("FirstRX on channel: "));
		Logger.appendByte(channel);
		Logger.appendString(csr.s2b(" Recieved: "));
		Logger.appendByte(data[11]);
		Logger.flush(Mote.WARN);
		sink.firstRecieve = data[DATA_INDEX];
		sink.t = time;
		sink.state = 1;
		break;
	    case 1:
		Logger.appendString(csr.s2b("SecondRX on channel: "));
		Logger.appendByte(channel);
		Logger.appendString(csr.s2b(" Recieved: "));
		Logger.appendByte(data[11]);
		Logger.flush(Mote.WARN);
		sink.t = time - sink.t;
		sink.state = 2;
		setTimer(channel, (sink.t * data[DATA_INDEX]));
		break;
	    case 3:
		Logger.appendString(csr.s2b("NCalc on channel: "));
		Logger.appendByte(channel);
		Logger.appendString(csr.s2b(" n is: "));
		Logger.appendByte(data[11]);
		Logger.flush(Mote.WARN);
		sink.n = data[DATA_INDEX];
		setTimer(channel, sink.t * sink.n);
		sink.state = 4;
		setChannel(returnChannel);
		break;
	}
	return 0;
    }
    
    private static void handleAlarmCallaback(byte param, long time){
//	Logger.appendString(csr.s2b("Alarm Callback on Channel: "));
//	Logger.appendByte(param);
//	Logger.flush(Mote.WARN);
	returnChannel = radio.getChannel();
	setChannel(param);
	SinkData sink = sinkStore[param];
	if (sink.state != 3){
	    handleTX(param, time);
	}
	else{
	    radio.startRx(Device.ASAP | Device.RX4EVER, 0, 0);
	}
    }
    
    private static void handleTX(byte param, long time){
	
	SinkData sink = sinkStore[param];
	transmitByte = new byte[16];
	transmitByte[0] = Radio.FCF_BEACON;
	transmitByte[1] = Radio.FCA_SRC_SADDR | Radio.FCA_DST_SADDR;
	Util.set16le(transmitByte, 3, PAN); // Source PAN
	Util.set16le(transmitByte, 5, 0xFFFF); // Broadcast
	Util.set16le(transmitByte, 7, PAN); // Own PAN
	Util.set16le(transmitByte, 9, 0x15); // Own Short Address
	
	Logger.appendString(csr.s2b("Transmitting on channel: "));
	Logger.appendByte(param);
	Logger.flush(Mote.WARN);
	radio.transmit(Device.ASAP|Radio.TXMODE_CCA, transmitByte, 0, 16, 0);
	
	if (sink.state == 2){
	    setTimer(param,(sink.t * 11));
	    sink.state = 3;
	}
	else if (sink.state == 4){
	    setTimer(param, (sink.t * 10) + (sink.t * sink.n));
	}
	setChannel(returnChannel);
    }
    
    private static void setTimer(byte channel, long time){
	Logger.appendString(csr.s2b("Setting Timer on channel: "));
	Logger.appendByte(channel);
	Logger.flush(Mote.WARN);
	switch(channel){
	    case 0x11:
	    case 0:
		sink0Tmr.setAlarmBySpan(time);
		break;
	    case 0x12:
	    case 1:
		sink1Tmr.setAlarmBySpan(time);
		break;
	    case 0x13:
	    case 2:
		sink1Tmr.setAlarmBySpan(time);
		break;
	}
    }
    private static void setChannel(byte channel){
	Logger.appendString(csr.s2b("Setting Channel: "));
	Logger.appendByte(channel);
	Logger.flush(Mote.WARN);
	if (radio.getState() == Device.S_RXEN){
	    radio.stopRx();
	}
	switch(channel){
	    case 0:
		PAN = 0x11;
		SHORT_ADDR = 0x11;
		break;
	    case 1:
		PAN = 0x12;
		SHORT_ADDR = 0x12;
		break;
	    case 2:
		PAN = 0x13;
		SHORT_ADDR = 0x12;
		break;
	}
	radio.setChannel(channel);
	radio.setPanId(PAN, true);
    }
    
    private static void nextChannel(){
	return;
    }
} 
