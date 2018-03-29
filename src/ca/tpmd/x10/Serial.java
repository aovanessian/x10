package ca.tpmd.x10;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Calendar;

public final class Serial implements Runnable
{

private final static Code HOUSE = Code.O;
private final static int DELAY = 5;
private final static byte[] _buf = new byte[64];
private final static byte[] _sbuf = new byte[64];
private final String _name;
private static SerialPort _port;
private final static int[] _codes = {0x6, 0xe, 0x2, 0xa, 0x1, 0x9, 0x5, 0xd, 0x7, 0xf, 0x3, 0xb, 0x0, 0x8, 0x4, 0xc};
private volatile int _data_len = 0;
private static Serial _serial = null;

public static Serial create(String name)
{
	if (_serial == null)
		_serial = new Serial(name);
	return _serial;
}

private Serial(String name)
{
	_name = name;
}

public static final void list_ports()
{
	SerialPort[] ports = SerialPort.getCommPorts();
	for (int i = 0; i < ports.length; i++)
		X10.info(port_settings(ports[i]));
}

private static void delay(int ms)
{
	try {
		Thread.sleep(ms);
	} catch (Exception e) {
		e.printStackTrace();
	}
}

public void readData()
{
	delay(50);
	int n = _port.readBytes(_buf, _port.bytesAvailable());
	_data_len = n;
	X10.debug("\tGot  " + X10.hex(_buf, n) + " (checksum: " + checksum(_buf, n) + ")");
}

private void setup()
{
	_port = SerialPort.getCommPort(_name);
	_port.setComPortParameters(4800, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
	_port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
	_port.addDataListener(new Listener(this));
	_port.openPort();
	X10.debug(port_settings(_port));
}

private void teardown()
{
	if (_port == null)
		return;
	_port.closePort();
	X10.debug(port_settings(_port));
	_port = null;
	_serial = null;
}

private static String device_string(int house, int unit)
{
	StringBuilder result = new StringBuilder(2);
	result.append((char)(house & 0xff));
	result.append(unit);
	return result.toString();
}

private static byte device(int n)
{
	return (byte)_codes[n - 1];
}

private static int checksum(byte[] buf, int n)
{
	int z = 0;
	for (int i = 0; i < n; i++)
		z+= buf[i];
	return z & 0xff;
}

private int listen(int ms)
{
	int z = ms / DELAY;
	int k = z;
	do {
		if (_data_len > 0) {
			int result = _data_len;
			_data_len = 0;
			X10.timing("\t\tListen delay: " + ms + "ms,\twaited for " + DELAY * (k - z) + "ms");
			return result;
		}
		delay(DELAY);
	} while (z-- > 0);
	X10.timing("\t\tListen delay: " + ms + "ms,\ttimed out");
	return 0;
}

private int command(byte[] buf, int n, int delay)
{
	int check = checksum(buf, n);
	int z = 0, k;
	do {
		if ((_buf[0] & 0xff) == 0x5a) {
			X10.verbose("!\tInterface wants to send data, aborting command");
			return 1;
		}
		send(buf, n);
		k = listen(100);
		if (k == 0) {
			X10.verbose("!\tInterface did not respond, aborting command");
			return 2;
		}
		z = checksum(_buf, k);
	} while (z != check);
	send(0);
	listen(delay);
	if ((_buf[0] & 0xff) == 0x55) {
		X10.debug("\tCommand successful");
		return 0;
	}
	return 2;
}

private void send(int b)
{
	byte[] buf = {(byte)(b & 0xff)};
	send(buf, 1);
}

private void send(byte[] buf, int n)
{
	X10.debug("\tSent " + X10.hex(buf, n) + " (checksum: " + checksum(buf, n) + ")");
	_port.writeBytes(buf, n);
}

private int address(int house, int unit)
{
	X10.debug("\tAddressing " + device_string(house, unit));
	_sbuf[0] = (byte)4;
	_sbuf[1] = (byte)(house << 4 | device(unit));
	return command(_sbuf, 2, 600);
}

private int function(int house, int dim, int command)
{
	X10.debug("\tFunction " + command + ", dim " + dim);
	_sbuf[0] = (byte)((dim << 3) | 6);
	_sbuf[1] = (byte)(house << 4 | command);
	return command(_sbuf, 2, 800 + dim * 200);
}

private static long time()
{
	return System.currentTimeMillis();
}

private static void time(long t)
{
	X10.timing("Took " + (System.currentTimeMillis() - t) + "ms");
}

private void parse_status(int n)
{
	X10.info("Status data: " + X10.hex(_buf, n));
	int k = (_buf[0] & 0xff) + 1;
	if (n > 11) {
		X10.err("Status too long (" + n + " bytes)");
		return;
	}
	if (n != k) {
		X10.err("Truncated status: only " + n + " bytes out of " + k + " available");
		return;
	}
	X10.debug("Got " + k + " bytes to parse");
	int map = _buf[1] & 0xff;
	int p = 2;
	int b;
	int z;
	StringBuilder s = new StringBuilder();
	while (p < k) {
		b = map & 1;
		map >>>= 1;
		X10.debug("Byte at " + p + " is " + (b == 0 ? "address" : "function"));
		z = _buf[p] & 0xff;
		switch (b) {
		case 0:
			s.append(Code.lookup(z >>> 4));
			s.append((Code.lookup(z & 0xf).toString().charAt(0) - 'A' + 1) & 0xff);
			s.append(" ");
			break;
		case 1:
			Cmd func = Cmd.lookup(z & 0xf);
			s.append(func.label());
			switch (func) {
			case DIM:
				s.append("m");
			case BRIGHT:
				s.append("ed by ");
				s.append((_buf[++p] & 0xff) * 100 / 210);
				s.append("%");
				break;
			case EXT_CODE:
				s.append("data: ");
				s.append(X10.hex(_buf[++p]));
				s.append(", command: ");
				s.append(X10.hex(_buf[++p]));
				break;
			}
			s.append("; ");
		}
		p++;
	}
	X10.info(s.toString());
}

private boolean cmd(Command c)
{
	long t = time();
	X10.info(c.toString());
	int result = 0;
	int house = c.houseCode();
	int[] units = c.units();
	if (units != null)
		for (int i = 0; i < units.length; i++)
			if ((result = address(house, units[i])) != 0)
				break;
	if (result == 0)
		function(house, c.dim(), c.cmdCode());
	time(t);
	return result == 0;
	
}

private static final String pad(int n)
{
	return (n < 10) ? "0" + n : "" + n;
}

private void set_clock(Code house)
{
	String[] weekdays = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
	Calendar calendar = Calendar.getInstance();
	int wd = calendar.get(Calendar.DAY_OF_WEEK);
	int day = calendar.get(Calendar.DAY_OF_YEAR);
	int hour = calendar.get(Calendar.HOUR);
	int minute = calendar.get(Calendar.MINUTE);
	int second = calendar.get(Calendar.SECOND);
	X10.debug(weekdays[wd] + ", " + day + " day or year " + pad(hour) + ":" + pad(minute) + ":" + pad(second));
	int clear = 0;//1;

	_sbuf[0] = (byte)0x9b;			/* CM11A timer download code */
	_sbuf[1] = (byte)second;
	_sbuf[2] = (byte)(minute + ((hour & 1) * 60))  ;  /* minutes 0 - 119 */
	_sbuf[3] = (byte)(hour >>> 1);		/* hour / 2         0 - 11 */
	_sbuf[4] = (byte)(day & 0xff);		/* mantisa of julian date */
	_sbuf[5] = (byte)((day >>> 15 ) << 7);	/* radix of julian date */
	_sbuf[5] |= (byte)(1 << wd);		/* bits 0-6 = single bit mask day of week ( smtwtfs ) */
	_sbuf[6] = (byte)((house.ordinal() << 4) | clear);
	send(_sbuf, 7);
	X10.info("Clock set response: " + X10.hex(_buf, listen(800)));
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

private static final int a1 = 1; // O1	HD501 rf receiver + appliance mmodule 2 prong
private static final int a2 = 3; // O3	RR466 appliance module 3 prong
private static final int d1 = 5; // O5	HD465 dimmer module
private static final int d2 = 7; // 07	WS467 dimmer switch

public void run()
{
	setup();
	int z = 10, k;
	int i = 0;
	Command command;
	do {
		listen(50);
		switch (_buf[0] & 0xff) {
		//case 0x5b:
		//	comm.listen(50);
		//	break;
		case 0x5a:
			k = 0;
			X10.debug("Interface has data for us");
			while ((_buf[0] & 0xff) == 0x5a) {
				send(0xc3);
				k = listen(500);
			}
			parse_status(k);
			break;
		case 0xa5:
			X10.debug("Interface asks for clock");
			set_clock(HOUSE);
		}
		command = getCommand();
		if (command == null)
			continue;
		if (command.exit())
			break;
		if (!command.cmdSystem())
			cmd(command);
//		delay(200);
	} while (--z > 0);
	teardown();
}

private static int _z = 5;

private Command getCommand()
{
	return (_z-- == 0) ? new Command(Cmd.EXIT, null) : new Command(Cmd.ALL_OFF, Code.M);
}

}

