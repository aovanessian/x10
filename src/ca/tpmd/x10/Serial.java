package ca.tpmd.x10;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Calendar;
import java.util.ArrayList;

public final class Serial implements Runnable
{

private final static Code HOUSE = Code.O;
private final static int DELAY = 5;
private final static byte[] _buf = new byte[64];
private final static byte[] _sbuf = new byte[64];
private final String _name;
private static SerialPort _port;
private final static int[] _codes = {0x6, 0xe, 0x2, 0xa, 0x1, 0x9, 0x5, 0xd, 0x7, 0xf, 0x3, 0xb, 0x0, 0x8, 0x4, 0xc};
private final static String[] _weekdays = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
private volatile int _data_len = 0;
private static Serial _serial = null;
private static ArrayList<Command> _commands = new ArrayList<Command>();

public static Serial create(String name)
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

public boolean test()
{
	if (!_port.isOpen()) {
		X10.log(0, "Could not open port");
		return false;
	}
	_sbuf[0] = (byte)((1 << 3) | 6);
        _sbuf[1] = (byte)(0xf);
	send(_sbuf, 2);
	return (listen(1500) != 0);
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
	return checksum(buf, 0, n);
}

private static int checksum(byte[] buf, int s, int n)
{
	int z = 0;
	for (int i = s; i < n; i++)
		z += buf[i];
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
	return command(buf, 0, n, delay);
}

private int command(byte[] buf, int s, int n, int delay)
{
	int check = checksum(buf, s, n);
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
	send(buf, 0, n);
}

private void send(byte[] buf, int s, int n)
{
	X10.debug("\tSent " + X10.hex(buf, n) + " (checksum: " + checksum(buf, s, n) + ")");
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
			case EXT_CODE_2:
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

private boolean parse_state(int n)
{
	if (n != 14) {
		X10.err("Expect 14 bytes state response, got " + n + " bytes");
		return false;
	}
	StringBuilder s = new StringBuilder();
	int z;
	s.append("State: ");
	s.append(X10.hex(_buf, 14));
	s.append("\n\tFirmware revision: ");
	s.append(_buf[7] & 0xf);
	s.append("\n\tMinutes on battery power: ");
	z = (_buf[0] << 8 | _buf[1]) & 0xffff;
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
	s.append("\n\t");
	s.append(calendar.getTime());
	s.append("\n\tTime: ");
	s.append((_buf[4] << 1) + (_buf[3] / 60));
	s.append(":");
	s.append(pad(_buf[3] % 60));
	s.append(":");
	s.append(pad(_buf[2]));
	s.append("\n\tMonitored house code: ");
	String house = Code.lookup((_buf[7] >>> 4) & 0xf).toString();
	s.append(house);
	s.append("\n\t");
	for (i = 0; i < 16; i++) {
		s.append(house);
		s.append(i + 1);
		s.append("\t");
	}
	int off = (_buf[11] << 8 | _buf[10]) & 0xffff;
	int dim = (_buf[13] << 8 | _buf[12]) & 0xffff;
	s.append("\n\t");
	for (i = 0; i < 16; i++) {
		int shift = 1 << Code.find(i).ordinal();
		s.append((dim & shift) == 0 ? ((off & shift) == 0 ? "off" : "on") : "dimmed");
		s.append("\t");
	}
	X10.info(s.toString());
	return true;
}

private boolean cmd(Command c)
{
	long t = time();
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

private boolean sys_cmd(Command c)
{
	switch (c.cmd()) {
	case SYSTEM_STATE:
		send(0x8b);
		return parse_state(listen(100));
	case RING_DISABLE:
		return sys_cmd(0xdb);
	case RING_ENABLE:
		return sys_cmd(0xeb);
	case CLOCK_SET:
		return set_clock(c.houseCode(), 0);
	}
	return false;
}

private boolean sys_cmd(int cmd)
{
	_sbuf[0] = (byte)cmd;
	return command(_sbuf, 1, 100) == 0;
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

	_sbuf[0] = (byte)0x9b;
	_sbuf[1] = (byte)second;
	_sbuf[2] = (byte)(minute + ((hour & 1) * 60));
	_sbuf[3] = (byte)(hour >>> 1);
	_sbuf[4] = (byte)(day & 0xff);
	_sbuf[5] = (byte)((day >>> 15 ) << 7);
	_sbuf[5] |= (byte)(1 << wd);
	_sbuf[6] = (byte)((house << 4) | clear);
	if (command(_sbuf, 1, 7, 200) != 0) {
		X10.verbose("!\tInterface did not respond, aborting clock setting.");
		return false;
	}
	X10.info("Clock set");
	return true;
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
	int k;
	int i = 0;
	Command command;
	for (;;) {
		listen(5);
		switch (_buf[0] & 0xff) {
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
			set_clock(HOUSE.ordinal(), 3);
		}
		command = getCommand();
		if (command == null)
			continue;
		X10.verbose(command.toString());
		if (command.exit())
			break;
		if (!command.cmdSystem()) {
			if (cmd(command)) {
				X10.info(command + " complete");
				_commands.remove(0);
			}
			continue;
		}
		if (sys_cmd(command))
			X10.info(command + " complete");
		else
			X10.warn(command + " unsuccessful");
		_commands.remove(0);
	}
	teardown();
}

private synchronized Command getCommand()
{
	Command cmd = _commands.isEmpty() ? null : _commands.get(0);
	if (cmd == null)
		try {
			wait(1000);
		} catch (InterruptedException x) {}
	return cmd;
}

public synchronized void addCommand(Command cmd)
{
	_commands.add(cmd);
	notify();
}

}

