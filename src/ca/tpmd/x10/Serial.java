package ca.tpmd.x10;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Calendar;
import java.util.ArrayList;

public final class Serial implements Runnable
{

private final static Code HOUSE = Code.O;
private final static byte[] _buf = new byte[32]; // feeling generous here
private final static byte[] _sbuf = new byte[32];
private final String _name;
private static SerialPort _port;
private final static int[] _codes = {0x6, 0xe, 0x2, 0xa, 0x1, 0x9, 0x5, 0xd, 0x7, 0xf, 0x3, 0xb, 0x0, 0x8, 0x4, 0xc};
private final static String[] _weekdays = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
private static Serial _serial = null;
private static ArrayList<Command> _commands = new ArrayList<Command>();
private static final int DELAY = 25;
private static final int RETRIES = 4;

private static final int ST_POWER_OUTAGE = 0xa5;
private static final int ST_HAVE_DATA = 0x5a;
private static final int ST_READY = 0x55;

private static final int CMD_SEND = 0xc3;
private static final int CMD_STATE = 0x8b;
private static final int CMD_CLOCK = 0x9b;
private static final int CMD_RING_DISABLE = 0xdb;
private static final int CMD_RING_ENABLE = 0xeb;
private static final int CMD_DL_EEPROM = 0xfb;

public static synchronized Serial create(String name)
{
	if (_serial == null)
		_serial = new Serial(name);
	return _serial;
}

private Serial(String name)
{
	_name = name;
	setup();
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
		long z = time();
		Thread.sleep(ms);
		X10.timing("Slept for " + time(z));
	} catch (InterruptedException e) {
		e.printStackTrace();
	}
}

public synchronized void data_available()
{
	notify();
}

public boolean test()
{
	if (!_port.isOpen()) {
		X10.log(0, "Could not open port");
		return false;
	}
	if (listen(false) > 0)
		return true;
	send(CMD_RING_ENABLE, 1200);
	return (listen(false) > 0);
}

private void setup()
{
	_port = SerialPort.getCommPort(_name);
	_port.setComPortParameters(4800, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
	_port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
	_port.addDataListener(new Listener(this));
	_port.openPort();
	X10.debug(port_settings(_port));
}

private static synchronized void teardown()
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

private static int checksum(byte[] buf, int s, int n)
{
	int z = 0;
	for (int i = s; i < n; i++)
		z += buf[i];
	return z & 0xff;
}

private int listen(boolean delay)
{
	if (delay) {
		delay(3); // wait 3ms to see if there's another byte coming, which at 4800 kbps 8N1 should take around 2.1ms
		if (_port.bytesAvailable() > 1) // yep, it's more than one
			delay(27); // just enough time to receive 12 more bytes of 14 byte state transmission (which seems to be the longest one)
	}
	int n = _port.readBytes(_buf, _port.bytesAvailable());
	if (n != 0)
		X10.debug("Got  " + X10.hex(_buf, n) + " (checksum: " + X10.hex(checksum(_buf, 0, n)) + ")");
	return n;
}

private boolean command(int n, int d)
{
	int check = _sbuf[n];
	int k = n << 4;
	do {
		if ((_buf[0] & 0xff) == ST_HAVE_DATA) {
			X10.info("Interface has data, aborting command");
			return false;
		}
		send_buf(n, k);
		if (listen(false) == 0) {
			X10.warn("Interface did not respond within " + k + "ms, aborting command");
			return false;
		}
	} while (_buf[0] != check);
	send(0, d);
	listen(false);
	return (_buf[0] & 0xff) == ST_READY;
}

private void send(int b, int d)
{
	_sbuf[0] = _sbuf[1] = (byte)(b & 0xff);
	send_buf(1, d);
}

private void send_buf(int n, int d)
{
	_port.writeBytes(_sbuf, n);
	long a = time() + d;
	X10.debug("Sent " + X10.hex(_sbuf, n) + " (checksum: " + X10.hex(_sbuf[n]) + ")");
	int s = d;
	for (;;) {
		sleep(s);
		if (_port.bytesAvailable() > 0) // got data
			return;
		n = (int)(a - time());
		if (n <= 0) // timed out
			return;
		s = d - n;
	}
}

private boolean address(int house, int unit)
{
	X10.debug("Addressing " + device_string(house, unit));
	_sbuf[0] = (byte)0xc;
	_sbuf[1] = (byte)(house << 4 | device(unit));
	_sbuf[2] = (byte)checksum(_sbuf, 0, 2);
	return command(2, 500);
}

private boolean function(int house, int dim, int command)
{
	X10.debug("Function " + command + ", dim " + dim);
	_sbuf[0] = (byte)((dim << 3) | 6);
	_sbuf[1] = (byte)(house << 4 | command);
	_sbuf[2] = (byte)checksum(_sbuf, 0, 2);
	return command(2, 300 + dim * 200);
}

private static long time()
{
	return System.currentTimeMillis();
}

private static String time(long t)
{
	return (time() - t) + "ms";
}

private void parse_status(int n)
{
	X10.info("Status data: " + X10.hex(_buf, n));
	if (n > 11) {
		X10.warn("Status too long (" + n + " bytes)");
		return;
	}
	int k = (_buf[0] & 0xff) + 1;
	if (n != k) {
		X10.warn("Truncated status: " + n + " out of " + k + " bytes available");
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
		X10.debug("Byte at " + p + " is " + (b == 0 ? "address" : "function"));
		z = _buf[p++] & 0xff;
		map >>>= 1;
		if (b == 0) {
			s.append(Code.lookup(z >>> 4));
			s.append((Code.lookup(z & 0xf).toString().charAt(0) - 'A' + 1) & 0xff);
			s.append(" ");
			continue;
		}
		Cmd func = Cmd.lookup(z & 0xf);
		s.append(func.label());
		switch (func) {
		case DIM:
			s.append("m");
		case BRIGHT:
			s.append("ed by ");
			s.append((_buf[p++] & 0xff) * 100 / 210);
			s.append("%");
			map >>>= 1;
			break;
		case EXT_CODE_1:
			s.append(" data: ");
			s.append(X10.hex(_buf[p++]));
			s.append(", command: ");
			s.append(X10.hex(_buf[p++]));
			s.append(", unit: ");
			s.append(X10.hex(_buf[p++]));
			map >>>= 3;
			break;
		}
		s.append("; ");
	}
	X10.info(s.toString());
}

private boolean parse_state(int n)
{
	if (n != 14) {
		X10.warn("Truncated state: " + n + " out of 14 bytes available");
		return false;
	}
	StringBuilder s = new StringBuilder();
	int z;
	s.append("State: ");
	s.append(X10.hex(_buf, 14));
	s.append("\n\tFirmware revision: ");
	s.append(_buf[7] & 0xf);
	s.append("\n\tMinutes on battery power: ");
	z = (_buf[1] << 8 | _buf[0] & 0xff) & 0xffff;
	s.append(z == 0xffff ? "unknown, needs clear" : z);
	z = _buf[5] & 0xff | _buf[6] & 0x80;
	Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_YEAR, z);
	s.append("\n\tDay of year: ");
	s.append(z);
	s.append(", ");
	z = _buf[6] & 0x7f;
	int i = 0;
	while ((z >>>= 1) != 0)
		i++;
	s.append(_weekdays[i]);
	s.append(" ");
	s.append((_buf[4] << 1) + (_buf[3] / 60));
	s.append(":");
	s.append(pad(_buf[3] % 60));
	s.append(":");
	s.append(pad(_buf[2]));
	s.append(" (sanity check: ");
	s.append(calendar.getTime());
	String house = Code.lookup((_buf[7] >>> 4) & 0xf).toString();
	s.append(")\n\n\t");
	int addr = (_buf[9] << 8 | _buf[8] & 0xff) & 0xffff;
	int off = (_buf[11] << 8 | _buf[10] & 0xff) & 0xffff;
	int dim = (_buf[13] << 8 | _buf[12] & 0xff) & 0xffff;
	StringBuilder a = new StringBuilder("\n");
	StringBuilder b = new StringBuilder("\n");
	int mask;
	for (i = 0; i < 16; i++) {
		mask = 1 << Code.find(i).ordinal();
		s.append((addr & mask) != 0 ? "[" : "");
		s.append(house);
		s.append(i + 1);
		s.append((addr & mask) != 0 ? "]\t" : "\t");
		a.append((off & mask) != 0 ? "\ton" : "\toff");
		b.append((dim & mask) != 0 ? "\tdimmed" : "\t");
	}
	s.append(a);
	s.append(b);
	X10.info(s.toString());
	return true;
}

private boolean cmd(Command c)
{
	int house = c.houseCode();
	int[] units = c.units();
	if (units != null)
		for (int i = 0; i < units.length; i++)
			if (!address(house, units[i]))
				return false;
	return function(house, c.dim(), c.cmdCode());
}

private static final String pad(int n)
{
	return (n < 10) ? "0" + n : "" + n;
}

private boolean sys_cmd(Command c)
{
	switch (c.cmd()) {
	case SYSTEM_STATE:
		send(CMD_STATE, DELAY);
		return parse_state(listen(true));
	case RING_DISABLE:
		return sys_cmd(CMD_RING_DISABLE);
	case RING_ENABLE:
		return sys_cmd(CMD_RING_ENABLE);
	case CLOCK_SET:
		return set_clock(c.houseCode(), 0);
	}
	X10.warn("Command " + c.cmd() + " not implemented");
	return false;
}

private boolean sys_cmd(int cmd)
{
	_sbuf[0] = _sbuf[1] = (byte)cmd;
	return command(1, DELAY);
}

private boolean set_clock(int house, int clear)
{
	Calendar calendar = Calendar.getInstance();
	int wd = calendar.get(Calendar.DAY_OF_WEEK) - 1;
	int day = calendar.get(Calendar.DAY_OF_YEAR);
	int hour = calendar.get(Calendar.HOUR_OF_DAY);
	int minute = calendar.get(Calendar.MINUTE);
	int second = calendar.get(Calendar.SECOND);
	X10.debug(_weekdays[wd] + ", day of year: " + day + ", time " + hour + ":" + pad(minute) + ":" + pad(second));
	_sbuf[0] = (byte)CMD_CLOCK;
	_sbuf[1] = (byte)second;
	_sbuf[2] = (byte)(minute + ((hour & 1) * 60));
	_sbuf[3] = (byte)(hour >> 1);
	_sbuf[4] = (byte)(day & 0xff);
	_sbuf[5] = (byte)((day >>> 15 ) << 7);
	_sbuf[5] |= (byte)(1 << wd);
	_sbuf[6] = (byte)((house << 4) | clear);
	_sbuf[7] = (byte)checksum(_sbuf, 1, 7);
	return command(7, DELAY);
}

private static final String port_settings(SerialPort port)
{
	if (port == null)
		return "Null port descriptor";
	StringBuilder result = new StringBuilder("\n\t");
	result.append(port.getSystemPortName());
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

public void run()
{
	int i = RETRIES;
	long t;
	Command command;
	for (;;) {
		listen(false);
		t = time();
		switch (_buf[0] & 0xff) {
		case ST_HAVE_DATA:
			X10.debug("Interface has data for us");
			send(CMD_SEND, DELAY);
			parse_status(listen(true));
			X10.info("Status parsed in " + time(t));
			break;
		case ST_POWER_OUTAGE:
			X10.info("Interface reports power outage");
			send(CMD_CLOCK, DELAY);
			//set_clock(HOUSE.ordinal(), 3);
			//X10.info("Clock set in " + time(t));
		}
		command = getCommand();
		if (command == null)
			continue;
		X10.verbose(command.toString());
		if (command.exit())
			break;
		t = time();
		if (!command.cmdSystem()) {
			if (cmd(command)) {
				X10.info(command + " completed in " + time(t));
				//_commands.remove(0);
				i = 0;//RETRIES;
			}
			if (i == 0) {
				_commands.remove(0);
				i = RETRIES;
			}
			i--;
			continue;
		}
		if (sys_cmd(command))
			X10.info(command + " completed in " + time(t));
		else
			X10.warn(command + " unsuccessful");
		_commands.remove(0);
	}
	X10.info("shutting down serial interface");
	teardown();
}

private Command getCommand()
{
	if (_commands.isEmpty())
		sleep(0);
	return _commands.isEmpty() ? null : _commands.get(0);
}

public synchronized void addCommand(Command cmd)
{
	notify();
	_commands.add(cmd);
}

private synchronized void sleep(int timeout)
{
	long t = time();
	try {
		if (timeout == 0) {
			wait();
			X10.timing("Slept for " + time(t));
			return;
		}
		wait(timeout);
		X10.timing("Slept for " + time(t));
	} catch (InterruptedException x) {
		x.printStackTrace();
	}
}

}
