package ca.tpmd.x10;

import com.fazecast.jSerialComm.*;

public class Listener implements SerialPortDataListener
{

Comm comm;

public Listener(Comm c)
{
	comm = c;
}

public int getListeningEvents()
{
	return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
}

public void serialEvent(SerialPortEvent event)
{
	comm.readData();
}

}
