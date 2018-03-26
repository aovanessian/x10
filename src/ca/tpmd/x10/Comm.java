package ca.tpmd.x10;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Calendar;

public final class Comm
{

private final static Code HOUSE = Code.O;
private final static int DELAY = 10;
private final static char[] _hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
private final static byte[] _buf = new byte[64];
private final static byte[] _sbuf = new byte[64];
private final String _name;
private SerialPort _port;
private final static int[] _codes = {0x6, 0xe, 0x2, 0xa, 0x1, 0x9, 0x5, 0xd, 0x7, 0xf, 0x3, 0xb, 0x0, 0x8, 0x4, 0xc};
private volatile boolean _data_available = false;
private volatile int _data_len = 0;
private static final int DEBUG = 7;
private static final int TIMING = 6;
private static final int INFO = 5;
private static final int WARN = 4;
private static final int ERR = 3;
private static int _level = INFO;

public Comm(String name)
{
	_name = name;
}

public static final void list_ports()
{
	SerialPort[] ports = SerialPort.getCommPorts();
	for (int i = 0; i < ports.length; i++)
		log(INFO, port_settings(ports[i]));
}

public static final void log(int level, String msg)
{
	if (level <= _level)
		System.out.println(msg);
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
	log(DEBUG, port_settings(_port));
}

public void teardown()
{
	if (_port == null)
		return;
	_port.closePort();
	log(DEBUG, port_settings(_port));
	_port = null;
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

private int wait4data(int ms)
{
	int z = (ms == 0) ? 5000 / DELAY : ms / DELAY;
	int k = z;
	log(TIMING, "\t\twait4data delay: " + ms + "ms");
	do {
		if (_data_available && _data_len > 0) {
			_data_available = false;
			int result = _data_len;
			_data_len = 0;
			log(TIMING, "\t\tWaited for " + DELAY * (k - z) + "ms");
			return result;
		}
		delay(DELAY);
	} while (z-- > 0);
	log(TIMING, "\t\tTimed out waiting for data");
	return 0;
}

private void command(byte[] buf, int n, int delay)
{
	int check = checksum(buf, n);
	int z = 0, k;
	do {
		send(buf, n);
		k = wait4data(100);
		if (k == 0)
			continue;
		if ((_buf[0] & 0xff) == 0x5a) {
			log(WARN, "!\tInterface wants to send data, aborting command");
			return;
		}
		z = checksum(_buf, k);
	} while (z != check);
	send(0);
	k = wait4data(delay);
	if ((_buf[0] & 0xff) == 0x55)
		log(DEBUG, "\tCommand successful");
}

private void send(int b)
{
	byte[] buf = {(byte)(b & 0xff)};
	send(buf, 1);
}

public void send(byte[] buf, int n)
{
	log(DEBUG, "\tSent " + hex(buf, n) + " (checksum: " + checksum(buf, n) + ")");
	_port.writeBytes(buf, n);
}

public void address(int house, int unit)
{
	log(DEBUG, "\tAddressing " + device_string(house, unit));
	_sbuf[0] = (byte)4;
	_sbuf[1] = (byte)(house << 4 | device(unit));
	command(_sbuf, 2, 800);
}

public void function(int house, int dim, int command)
{
	log(DEBUG, "\tFunction " + command + ", dim " + dim);
	_sbuf[0] = (byte)((dim << 3) | 6);
	_sbuf[1] = (byte)(house << 4 | command);
	command(_sbuf, 2, 800 + dim * 200);
}

private static long time(String cmd)
{
	log(INFO, "Sending " + cmd + " command");
	return System.currentTimeMillis();
}

private static void time(long t)
{
	log(TIMING, "Took " + (System.currentTimeMillis() - t) + "ms");
}

public static void parse_status(int n)
{
	log(INFO, "Status data: " + hex(_buf, n));
	int k = (_buf[0] & 0xff) + 1;
	if (n > 11) {
		log(ERR, "Status too long (" + n + " bytes)");
		return;
	}
	if (n != k) {
		log(ERR, "Truncated status: only " + n + " bytes out of " + k + " available");
		return;
	}
	log(DEBUG, "Got " + k + " bytes to parse");
	int map = _buf[1] & 0xff;
	int p = 2;
	int b;
	int z;
	StringBuilder s = new StringBuilder();
	while (p < k) {
		b = map & 1;
		map >>>= 1;
		log(DEBUG, "Byte at " + p + " is " + (b == 0 ? "address" : "function"));
		z = _buf[p] & 0xff;
		switch (b) {
		case 0: //address
			s.append(Code.lookup(z >>> 4));
			s.append((Code.lookup(z & 0xf).toString().charAt(0) - 'A' + 1) & 0xff);
			s.append(" ");
			break;
		case 1: //function
			Cmd func = Cmd.lookup(z & 0xf);
			//s.append("House code: ");
			//s.append(hex(z >>> 4));
			//s.append(": ");
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
				s.append(hex(_buf[++p]));
				s.append(", command: ");
				s.append(hex(_buf[++p]));
				break;
			}
			s.append("; ");
		}
		p++;
	}
	log(INFO, s.toString());
}

public void cmd(Cmd func, Code house)
{
	if (func.need_addr()) {
		log(ERR, "Command '" + func.label() + "' needs address");
		return;
	}
	if (func.need_dim()) {
		log(ERR, "Command '" + func.label() + "' needs dim level");
		return;
	}
	cmd(func, house, null, 1);
}

public void cmd(Cmd func, Code house, int unit)
{
	if (!func.need_addr())
		log(WARN, "Command '" + func.label() + "' does not need an address");
	if (func.need_dim()) {
		log(ERR, "Command '" + func.label() + "' needs dim level");
		return;
	}
	int[] units = {unit};
	cmd(func, house, units, 1);
}

public void cmd(Cmd func, Code house, int unit, int dim)
{
	if (!func.need_addr())
		log(WARN, "Command '" + func.label() + "' does not need an address");
	if (!func.need_dim())
		log(WARN, "Command '" + func.label() + "' does not need dim level");
	int[] units = {unit};
	cmd(func, house, units, dim);
}

public void cmd(Cmd func, Code house, int[] units, int dim)
{
	long t = time(func.label());
	if (func.need_addr()) {
		for (int i = 0; i < units.length; i++)
			address(house.ordinal(), units[i]);
	}
	dim = (func.need_dim()) ? dim : 1;
	function(house.ordinal(), dim, func.ordinal());
	time(t);
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
	log(DEBUG, weekdays[wd] + ", " + day + " day or year " + pad(hour) + ":" + pad(minute) + ":" + pad(second));
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
	int k = wait4data(800);
	log(INFO, "Clock set response: " + hex(_buf, k));
}

private static final String hex(int n)
{
	int c = n & 0xff;
	StringBuilder result = new StringBuilder(4);
	result.append("0x");
	result.append(_hex[c >>> 4]);
	result.append(_hex[c & 0xf]);
	return result.toString();
}

private static final String hex(byte[] buf, int n)
{
	int c;
	StringBuilder result = new StringBuilder(n * 5);
	for (int i = 0; i < n; i++) {
		c = buf[i] & 0xff;
		result.append(" 0x");
		result.append(_hex[c >>> 4]);
		result.append(_hex[c & 0xf]);
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

private static final int a1 = 1; // O1	HD501 rf receiver + appliance mmodule 2 prong
private static final int a2 = 3; // O3	RR466 appliance module 3 prong
private static final int d1 = 5; // O5	HD465 dimmer module
private static final int d2 = 7; // 07	WS467 dimmer switch

public static void main(String[] args)
{
	Comm comm = new Comm(args[0]);
	comm.setup();
//	comm.list_ports();
	int z = 20;
	int[] units = {a1, a2};
	do {
		comm.wait4data(1200);
		switch (_buf[0] & 0xff) {
		case 0x5a:
			log(DEBUG, "Interface has data for us");
			comm.send(0xc3);
			parse_status(comm.wait4data(500));
			break;
		case 0xa5:
			log(DEBUG, "Interface asks for clock");
			comm.set_clock(HOUSE);
		}
		//comm.cmd(Cmd.STATUS_REQ, HOUSE, a1);
		delay(1000);
	} while (--z > 0);
	comm.teardown();
}

}

