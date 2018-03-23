package ca.tpmd.x10;

import com.fazecast.jSerialComm.*;

public class Comm
{

public Comm()
{

}

public void list_ports()
{
	SerialPort[] ports = SerialPort.getCommPorts();
	for (int i = 0; i < ports.length; i++) {
		System.out.println(ports[i].getDescriptivePortName());
	}
}

public static void main(String[] args)
{
	Comm comm = new Comm();
	comm.list_ports();
}

}
