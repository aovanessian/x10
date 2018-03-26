package ca.tpmd.x10;

import com.fazecast.jSerialComm.*;

public class Listener implements SerialPortDataListener
{

Serial comm;

public Listener(Serial s)
{
	comm = s;
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
