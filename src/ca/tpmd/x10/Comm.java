package ca.tpmd.x10;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Calendar;

public class Comm
{

private final static byte HOUSE = 'O';
private final static int DELAY = 20;
private final static char[] _hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
private final static byte[] _buf = new byte[128];
private final String _name;
private SerialPort _port;
private final static int[] _codes = {0x6, 0xe, 0x2, 0xa, 0x1, 0x9, 0x5, 0xd, 0x7, 0xf, 0x3, 0xb, 0x0, 0x8, 0x4, 0xc};
private volatile boolean _data_available = false;
private volatile int _data_len = 0;
private static final int DEBUG = 7;
private static final int INFO = 6;
private static final int WARN = 5;
private static final int ERROR = 4;
private int _level = 6;

/*
	All Units Off			0000		0
	All Lights On			0001		1
	On				0010		2
	Off				0011		3
	Dim				0100		4
	Bright				0101		5
	All Lights Off			0110		6
	Extended Code			0111		7
	Hail Request			1000		8
	Hail Acknowledge		1001		9
	Pre-set Dim (1)			1010		A
	Pre-set Dim (2)			1011		B
	Extended Data Transfer		1100		C
	Status On			1101		D
	Status Off			1110		E
	Status Request			1111		F
*/

public Comm(String name)
{
	_name = name;
}

public final void list_ports()
{
	SerialPort[] ports = SerialPort.getCommPorts();
	for (int i = 0; i < ports.length; i++) {
		log(INFO, port_settings(ports[i]));
	}
}

public final void log(int level, String msg)
{
	if (level <= _level)
		System.out.println(msg);
}

public void readData()
{
	try {
		Thread.sleep(DELAY);
	} catch (Exception e) {
		e.printStackTrace();
	}
	int n = _port.readBytes(_buf, _port.bytesAvailable());
	_data_available = true;
	_data_len = n;
	log(DEBUG, "\tGot  " + hex(_buf, n) + " (checksum: " + checksum(_buf, n) + ")");
}

public void setup()
{
	_port = SerialPort.getCommPort(_name);
	_port.setComPortParameters(4800, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
	_port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
	_port.addDataListener(new Listener(this));
	_port.openPort();
}

public void teardown()
{
	if (_port == null)
		return;
	_port.closePort();
	port_settings(_port);
	_port = null;
}

private String device_string(int house, int unit)
{
	StringBuilder result = new StringBuilder(2);
	result.append((char)((house) & 0xff));
	result.append(unit);
	return result.toString();
}

private byte device(int n)
{
	return (byte)_codes[n - 1];
}

private byte house(int n)
{
	int z = (n < 81) ? _codes[n - 65] : _codes[n - 97];
	return (byte)(z << 4);
}

private int checksum(byte[] buf, int n)
{
	int z = 0;
	for (int i = 0; i < n; i++)
		z+= buf[i];
	return z & 0xff;
}

private int wait4data()
{
	int z = 250;
	do {
		if (_data_available && _data_len > 0) {
			_data_available = false;
			int result = _data_len;
			_data_len = 0;
			log(DEBUG, "\t\tWaited for " + DELAY * (250 - z) + "ms");
			return result;
		}
		try {
			Thread.sleep(DELAY);
		} catch (Exception e) {
			e.printStackTrace();
		}
	} while (z-- > 0);
	log(WARN, "\t\tTimed out waiting for data");
	return 0;
}

private void command(byte[] buf, int n)
{
	int check = checksum(buf, n);
	int z = 0, k;
	do {
		send(buf, n);
		k = wait4data();
		if (k == 0)
			continue;
		if (k == 1 && _buf[0] == 0xa5) {
			log(WARN, "!\tInterface wants to send data, aborting command");
			return;
		}
		z = checksum(_buf, k);
	} while (z != check);
	send(0);
	k = wait4data();
	if (checksum(_buf, k) == 0x55)
		log(DEBUG, "\tCommand successful");
}

private int send(int b)
{
	byte[] buf = new byte[1];
	buf[0] = (byte)(b & 0xff);
	return send(buf, 1);
}

public int send(byte[] buf, int n)
{
	log(DEBUG, "\tSent " + hex(buf, n) + " (checksum: " + checksum(buf, n) + ")");
	return _port.writeBytes(buf, n);
}

public void address(int house, int unit)
{
	log(DEBUG, "\tAddressing " + device_string(house, unit));
	byte[] buf = new byte[2];
	buf[0] = (byte)4;
	buf[1] = (byte)(house(house) | device(unit));
	command(buf, 2);
}

public void function(int house, int dim, int command)
{
	log(DEBUG, "\tFunction " + command + ", dim " + dim);
	byte[] buf = new byte[2];
	buf[0] = (byte)((dim << 3) | 6);
	buf[1] = (byte)(house(house) | command);
	command(buf, 2);
}

private long time(String cmd)
{
	log(INFO, "Sending " + cmd + " command");
	return System.currentTimeMillis();
}

private void time(long t)
{
	log(INFO, "took " + (System.currentTimeMillis() - t) + "ms");
}

public void cmd_on(int house, int unit)
{
	long t = time("on");
	address(house, unit);
	function(house, 0, 2);
	time(t);
}

public void cmd_off(int house, int unit)
{
	long t = time("off");
	address(house, unit);
	function(house, 0, 3);
	time(t);
}

public void cmd_dim(int house, int unit, int dim)
{
	long t = time("dim");
	address(house, unit);
	function(house, dim, 4);
	time(t);
}

public void cmd_bright(int house, int unit, int dim)
{
	long t = time("bright");
	address(house, unit);
	function(house, dim, 5);
	time(t);
}

public void cmd_status(int house, int unit)
{
	long t = time("status");
	address(house, unit);
	function(house, 0, 0xf);
	int k = wait4data(); //expecting 0x5a
	if (k == 0) {
		log(WARN, "Device " + device_string(house, unit) + " not responding to status command");
	} else if ((_buf[0] & 0xff) == 0x5a) {
		log(DEBUG, "\tgetting status");
		send(0xc3);
		wait4data();
	}
	time(t);
}

private void set_clock()
{
	Calendar calendar = Calendar.getInstance();
	int wd = calendar.get(Calendar.DAY_OF_WEEK);
	int day = calendar.get(Calendar.DAY_OF_YEAR);
	int hour = calendar.get(Calendar.HOUR);
	int minute = calendar.get(Calendar.MINUTE);
	int second = calendar.get(Calendar.SECOND);
	log(DEBUG, wd + ", " + day + ", " + hour + ":" + minute + ":" + second);
	int clear = 0;//1;
	byte[] buf = new byte[7];

	buf[0] = (byte)0x9b;			/* CM11A timer download code */
	buf[1] = (byte)second;
	buf[2] = (byte)(minute + ((hour & 1) * 60))  ;  /* minutes 0 - 119 */
	buf[3] = (byte)(hour >>> 1);		/* hour / 2         0 - 11 */
	buf[4] = (byte)(day & 0xff);		/* mantisa of julian date */
	buf[5] = (byte)((day >>> 15 ) << 7);	/* radix of julian date */
	buf[5] |= (byte)(1 << wd);		/* bits 0-6 = single bit mask day of week ( smtwtfs ) */
	buf[6] = (byte)(house(HOUSE) | clear);
	command(buf, 7);
}

private String hex(byte[] buf, int n)
{
	int c;
	StringBuilder result = new StringBuilder(n * 5);
	for (int i = 0; i < n; i++) {
		c = buf[i] & 0xff;
		result.append(" 0x");
		result.append(_hex[c >>> 4]);
		result.append(_hex[c & 15]);
	}
	return result.toString();
}

private static final String port_settings(SerialPort port)
{
	if (port == null)
		return "Null port descriptor";
	StringBuilder result = new StringBuilder(port.getSystemPortName());
	result.append("\n\tName:         ");
	result.append(port.getDescriptivePortName());
	result.append("\n\tBaud rate:    ");
	result.append(port.getBaudRate());
	result.append("\n\tParity:       ");
	result.append(parity(port.getParity()));
	result.append("\n\tFlow control: ");
	result.append(flow(port.getFlowControlSettings()));
	result.append("\n\tData bits:    ");
	result.append(port.getNumDataBits());
	result.append("\n\tStop bits:    ");
	result.append(port.getNumStopBits());
	result.append("\n\tOpened:       ");
	result.append(port.isOpen());
	return result.toString();
}

private static final String flow(int f)
{
	switch (f) {
	case SerialPort.FLOW_CONTROL_DISABLED:
		return "none";
	case SerialPort.FLOW_CONTROL_CTS_ENABLED:
		return "CTS";
	case SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED:
		return "RTS/CTS";
	case SerialPort.FLOW_CONTROL_DSR_ENABLED:
		return "DSR";
	case SerialPort.FLOW_CONTROL_DTR_ENABLED | SerialPort.FLOW_CONTROL_DSR_ENABLED:
		return "DTR/DSR";
	case SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED:
		return "XOn/XOff";
	}
	return "unknown";
}

private static final String parity(int p)
{
	switch (p) {
	case SerialPort.NO_PARITY:
		return "none";
	case SerialPort.EVEN_PARITY:
		return "even";
	case SerialPort.ODD_PARITY:
		return "odd";
	case SerialPort.MARK_PARITY:
		return "mark";
	case SerialPort.SPACE_PARITY:
		return "space";
	}
	return "unknown";
}

public static void main(String[] args)
{
	Comm comm = new Comm(args[0]);
	//comm.list_ports();
	comm.setup();
//	comm.set_clock();

	comm.cmd_on(HOUSE, 1);
	comm.cmd_off(HOUSE, 1);
	comm.cmd_on(HOUSE, 8);
	comm.cmd_dim(HOUSE, 8, 11);
/*
	//comm.wait4data();
	comm.send(0xc3);
	comm.send(0xc3);
	comm.wait4data();
*/
/*
	comm.cmd_on(HOUSE, 1);
	comm.cmd_status(HOUSE, 1);
	comm.cmd_off(HOUSE, 1);
	comm.cmd_status(HOUSE, 1);
*/
	
//	comm.cmd_bright(HOUSE, 7, 22);
//	comm.cmd_dim(HOUSE, 7, 18);
//	comm.cmd_bright(HOUSE, 7, 22);
	//comm.cmd_off(HOUSE, 8);
	//comm.cmd_on('a', 1);
	comm.teardown();
}

}
