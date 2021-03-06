package ca.tpmd.x10;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Calendar;
import java.util.ArrayList;

final class Serial implements Runnable
{

private final static byte[] _buf = new byte[32]; // feeling generous here
private final static byte[] _sbuf = new byte[32];
private final String _name;
private static SerialPort _port;
private final static String[] _weekdays = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
private static Serial _serial = null;
private static ArrayList<Command> _commands = new ArrayList<Command>();
private int _last_addr = 0;
private static final int DELAY = 25;
private static final int RETRIES = 3;

private static final int ST_POWER_OUTAGE = 0xa5;
private static final int ST_HAVE_DATA = 0x5a;
private static final int ST_RAN_MACRO = 0x5b;
private static final int ST_READY = 0x55;

private static final int CMD_SEND = 0xc3;
private static final int CMD_STATE = 0x8b;
private static final int CMD_CLOCK = 0x9b;
private static final int CMD_RING_DISABLE = 0xdb;
private static final int CMD_RING_ENABLE = 0xeb;
private static final int CMD_EEPROM_DL = 0xfb;

private static final int RESET_MONITOR = 1;
private static final int RESET_BATTERY = 1 << 1;
private static final int RESET_TIMERS = 1 << 2;

static synchronized Serial create(String name)
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

static final void list_ports()
{
	SerialPort[] ports = SerialPort.getCommPorts();
	for (int i = 0; i < ports.length; i++)
		X10.info(port_settings(ports[i]));
}

private static void delay(int ms)
{
	long z = time();
	try {
		Thread.sleep(ms);
	} catch (InterruptedException e) {
		e.printStackTrace();
	}
	X10.timing("Slept for " + time(z));
}

synchronized void data_available()
{
	notify();
}

boolean test()
{
	return true;//_port.isOpen();
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
	_port.closePort();
	X10.debug(port_settings(_port));
	_port = null;
	_serial = null;
}

private static int checksum(byte[] buf, int s, int n)
{
	int z = 0;
	while (n-- > s)
		z += buf[n];
	return z & 0xff;
}

private int listen(boolean delay)
{
	if (delay) {
		delay(3); // wait 3ms to see if there's another byte coming, which at 4800 kbps 8N1 should take around 2.1ms
		if (_port.bytesAvailable() > 1) // yep, it's more than one
			//delay(27); // just enough time to receive 12 more bytes of 14 byte state transmission (which seems to be the longest one)
			delay(35);
	}
	int n = _port.readBytes(_buf, delay ? _port.bytesAvailable() : 1);
	if (n != 0)
		X10.debug("got  " + X10.hex(_buf, n) + " (checksum: " + X10.hex(checksum(_buf, 0, n)) + ")");
	return n;
}

private boolean command(int n, int d)
{
	int check = _sbuf[n];
	int k = 4 + (n << 3);
	//int k = n << 5;
	do {
		switch (_buf[0] & 0xff) {
		case ST_HAVE_DATA:
		case ST_RAN_MACRO:
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
	X10.debug("sent " + X10.hex(_sbuf, n) + " (checksum: " + X10.hex(_sbuf[n]) + ")");
	int s = d;
	X10.debug("will sleep for " + d + "ms");
	for (;;) {
		sleep(s);
		if (_port.bytesAvailable() > 0) {
			X10.debug("received data");
			return;
		}
		n = (int)(a - time());
		if (n <= 0) {
			X10.debug("wait() timed out");
			return;
		}
		s = d - n;
	}
}

private boolean address(int house, int unit)
{
	X10.debug("addressing " + X10.house(house) + X10.unit(unit));
	_sbuf[0] = (byte)0xc;
	_sbuf[1] = (byte)(house << 4 | unit);
	_sbuf[2] = (byte)checksum(_sbuf, 0, 2);
	return command(2, 500);
}

private boolean function(int house, int dim, int command)
{
	X10.debug("house " + X10.house(house) + ", function " + command + ", dim " + dim);
	_sbuf[0] = (byte)((dim << 3) | 6);
	_sbuf[1] = (byte)(house << 4 | command);
	_sbuf[2] = (byte)checksum(_sbuf, 0, 2);
	return command(2, 300 + dim * 200);
}

private boolean extended(int house, int unit, int data, int command)
{
	X10.debug("extended command: house " + X10.house(house) + ", unit " + X10.unit(unit) + ", data " + data + ", command " + command);
	_sbuf[0] = (byte)0xf;
	_sbuf[1] = (byte)(house << 4 | 7);
	_sbuf[2] = (byte)unit;
	_sbuf[3] = (byte)data;
	_sbuf[4] = (byte)command;
	_sbuf[5] = (byte)checksum(_sbuf, 0, 5);
	return command(5, 1300);
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
	X10.debug("got " + k + " bytes to parse");
	StringBuilder s = new StringBuilder();
	StringBuilder t = new StringBuilder();
	int map = _buf[1] & 0xff;
	int p = 2;
	if (_last_addr != 0) { // last transmission ended with an address - start parsing from that
		_buf[1] = (byte)(_last_addr & 0xff);
		p = 1;
		map <<= 1;
		s.append("\u2026");
	}
	int b, c, z;
	while (p < k) {
		z = _buf[p] & 0xff;
		b = map & 1;
		map >>>= 1;
		X10.debug("byte at " + p + " (" + X10.hex(z) + ") is " + (b == 0 ? "an address" : "a function"));
		p++;
		_last_addr = 0;
		if (b == 0) {
			s.append(X10.house(z >>> 4));
			s.append(X10.unit(z & 0xf));
			if (p == k) { // last byte is an address; function will come in next transmission
				_last_addr = z | 0x100;
				s.append("\u2026");
				break;
			}
			s.append(" ");
			continue;
		}
		t.setLength(0);
		t.append(X10.house(z >>> 4));
		t.append(" ");
		Cmd func = Cmd.lookup(z & 0xf);
		t.append(func.label());
		c = 0;
		switch (func) {
		case DIM:
			t.append("m");
		case BRIGHT:
			t.append("ed by ");
			t.append((_buf[p++] & 0xff) * 100 / 210);
			t.append("%");
			map >>>= 1;
			break;
		case EXT_CODE_1:
			t.append(" data: ");
			t.append(X10.hex(_buf[p++]));
			t.append(", command: ");
			t.append(X10.hex(_buf[p++]));
			t.append(", unit: ");
			t.append(X10.hex(_buf[p++]));
			map >>>= 3;
			break;
		case PRESET_DIM_2:
			c = 16;
		case PRESET_DIM_1:
			b = X10.le(z >>> 4) + c;
			t.setLength(0);
			t.append("preset dim level: ");
			t.append(b);
			t.append(" (");
			t.append(b * 100 / 31);
			t.append("%), temp: ");
			z = X10.unit(_buf[p - 2] & 0xf) - 11; // unit code in [11..16]
			c = (z << 5) + b - 60;
			t.append(c);
			t.append("\u00b0");
			if (c > 50) {
				t.append("F (");
				c = (int)Math.round((c - 32) * 50 / 9.);
				t.append(c / 10.);
				t.append("\u00b0C)");
			} else {
				t.append('C');
			}
			break;
		}
		s.append(t.toString());
		s.append("\n\t");
	}
	X10.info(s.toString());
}

private boolean parse_macro(int n)
{
	if (n != 2) {
		X10.warn("Expected 2 bytes, got " + n + " bytes (" + X10.hex(_buf, n) + ")");
		return false;
	}
	int addr = ((_buf[0] & 0x03) << 8 | _buf[1] & 0xff) & 0xffff;
	StringBuilder s = new StringBuilder();
	s.append("Raw data: ");
	s.append(X10.hex(_buf, 2));
	s.append("\n\tAddress: ");
	s.append(addr);
	s.append("\n\tStarted by ");
	s.append((_buf[0] & 0x70) == 0 ? "timer" : "trigger");
	X10.info(s.toString());
	return true;
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
	z = ((_buf[1] & 0xff) << 8 | (_buf[0] & 0xff)) & 0xffff;
	s.append(z == 0xffff ? "unknown - batteries dead?" : z);
	if (z != 0xffff && z > 60 * 24 * 3)
		s.append(" - replace batteries soon");
	z = _buf[5] & 0xff | ((_buf[6] & 0x80) << 1);
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
	s.append(((_buf[4] & 0xff) << 1) + ((_buf[3] & 0xff) / 60));
	s.append(":");
	s.append(pad((_buf[3] & 0xff) % 60));
	s.append(":");
	s.append(pad(_buf[2] & 0xff));
	s.append(" (sanity check: ");
	s.append(calendar.getTime());
	char house = X10.house((_buf[7] & 0xff) >>> 4);
	s.append(")\n\n\t");
	int addr = ((_buf[9] & 0xff) << 8 | _buf[8] & 0xff);
	int off = ((_buf[11] & 0xff) << 8 | _buf[10] & 0xff);
	int dim = ((_buf[13] & 0xff) << 8 | _buf[12] & 0xff);
	StringBuilder a = new StringBuilder("\n");
	StringBuilder b = new StringBuilder("\n");
	int mask;
	for (i = 0; i < 16; i++) {
		mask = 1 << X10.code(i);
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
	int house = X10.code(c.house());
	int units = c.units();
	if (units != 0) {
		int unit;
		for (int i = 0; i < 16; i++) {
			unit = X10.code(i);
			if ((units & (1 << unit)) == 0)
				continue;
			if (!address(house, unit))
				return false;
		}
	}
	return c.cmdCode() > 15 ? true : function(c.cmdHouse(), c.dim(), c.cmdCode());
}

private boolean xcmd(Command c)
{
	int house = X10.code(c.house());
	int units = c.units();
	int unit = 0;//X10.code(0);
	if (units != 0) {
		for (int i = 0; i < 16; i++) {
			unit = X10.code(i);
			if ((units & (1 << unit)) != 0)
				break;
		}
	}
	return extended(house, unit, c.xdata(), c.xcmd());
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
		return set_clock(X10.code((c.house())), 0);
	case RESET:
		return set_clock(0, RESET_TIMERS | RESET_MONITOR);
	case RESET_BATTERY:
		return set_clock(0, RESET_BATTERY);
	case EEPROM_ERASE:
		return eeprom_erase();
	case EEPROM_WRITE:
		return eeprom_write(c.getData());
	}
	X10.warn("Command " + c.cmd() + " not implemented");
	return false;
}

private boolean sys_cmd(int cmd)
{
	_sbuf[0] = _sbuf[1] = (byte)cmd;
	return command(1, DELAY);
}

private boolean eeprom_write(byte[] data)
{
	if (data.length != 1024) {
		X10.err("Image length is " + data.length + ", should be 1024 bytes");
		return false;
	}
	int n = 64, k, i;
	boolean result = eeprom_erase();
	boolean empty;
	while (result == true && n-- > 0) {
		k = n << 4;
		empty = true;
		for (i = k; i < k + 16; i++)
			if (data[i] != 0) {
				empty = false;
				break;
			}
		if (empty) {
			X10.verbose("Block " + (n + 1) + " empty, skipping write");
			continue;
		}
		System.arraycopy(data, k, _sbuf, 3, 16);
		_sbuf[0] = (byte)CMD_EEPROM_DL;
		_sbuf[1] = (byte)(n >>> 4);
		_sbuf[2] = (byte)(k & 0xf0);
		_sbuf[19] = (byte)checksum(_sbuf, 1, 19);
		X10.verbose("Writing block " + (n + 1));
		result = command(19, 2000);
	}
	return result;
}

private boolean eeprom_erase()
{
	_sbuf[0] = (byte)CMD_EEPROM_DL;
	_sbuf[1] = _sbuf[2] = _sbuf[3] = 0;
	_sbuf[4] = 2;
	for (int i = 5; i < 19; i++)
		_sbuf[i] = (byte)0xff;
	_sbuf[19] = (byte)checksum(_sbuf, 1, 19);
	return command(19, 2000);
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
	_sbuf[5] = (byte)((day >>> 8 ) << 7);
	_sbuf[5] |= (byte)(1 << wd);
	_sbuf[6] = (byte)((house << 4) | clear);
	_sbuf[7] = (byte)checksum(_sbuf, 1, 7);
	return command(7, clear == 0 ? DELAY : DELAY << 1);
}

private static final String port_settings(SerialPort port)
{
	if (port == null)
		return "Null port descriptor";
	StringBuilder result = new StringBuilder("\n\t");
	result.append(port.getSystemPortName());
	result.append("\n\tName:\t\t");
	result.append(port.getDescriptivePortName());
	result.append("\n\tBaud rate:\t");
	result.append(port.getBaudRate());
	result.append("\n\tParity:\t\t");
	result.append(parity(port.getParity()));
	result.append("\n\tFlow control:\t");
	result.append(flow(port.getFlowControlSettings()));
	result.append("\n\tData bits:\t");
	result.append(port.getNumDataBits());
	result.append("\n\tStop bits:\t");
	result.append(port.getNumStopBits());
	result.append("\n\tOpened:\t\t");
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
	int r = RETRIES;
	long t;
	int n;
	Command command;
	for (;;) {
		_buf[0] = 0;
		listen(false);
		t = time();
		switch (_buf[0] & 0xff) {
		case ST_READY: // sent by interface upon startup (_not_ 0xa5 as stated in protocol; that comes later)
			X10.info("Interface ready");
			sleep(1200); // wait for 'power out' message
			continue;
		case ST_HAVE_DATA:
			X10.debug("interface has data for us");
			do {
				send(CMD_SEND, DELAY);
				_buf[0] = 0;
				n = listen(true);
			} while (_buf[0] == ST_HAVE_DATA);
			parse_status(n);
			X10.info("Status parsed in " + time(t));
			break;
		case ST_RAN_MACRO:
			delay(20);
			parse_macro(listen(true));
			break;
		case ST_POWER_OUTAGE:
			X10.info("Interface reports power outage");
			send(CMD_CLOCK, 2000); // do not set clock again, just acknowledge message
		}
		command = getCommand();
		if (command == null)
			continue;
		X10.verbose(command.toString());
		if (command.exit())
			break;
		t = time();
		if (command.cmdSystem()) {
			if (sys_cmd(command))
				X10.info(command + " completed in " + time(t));
			else
				X10.warn(command + " unsuccessful");
			_commands.remove(0);
			continue;
		}
		if (command.cmdExtended() ? xcmd(command) : cmd(command)) {
			X10.info(command + " completed in " + time(t));
			_commands.remove(0);
			r = RETRIES;
			continue;
		}
		if (--r == 0) { // interface not responding - either power is out or there's traffic on the line
			X10.warn(command + " unsuccessful " + RETRIES + " times, will wait for interface to wake us up");
			r = RETRIES;
			sleep(0); // we'll get woken up when inerface sends data (or more commands added)
		}
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

synchronized void addCommand(Command cmd)
{
	notify();
	_commands.add(cmd);
}

private synchronized void sleep(int timeout)
{
	long t = time();
	try {
		if (timeout == 0)
			wait();
		else
			wait(timeout);
	} catch (InterruptedException x) {
		x.printStackTrace();
	}
	X10.timing("Slept for " + time(t));
}

}
