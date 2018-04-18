package ca.tpmd.x10;

import com.fazecast.jSerialComm.*;

final class Listener implements SerialPortDataListener
{

Serial comm;

Listener(Serial s)
{
	comm = s;
}

public int getListeningEvents()
{
	return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
}

public void serialEvent(SerialPortEvent event)
{
	comm.data_available();
}

}
