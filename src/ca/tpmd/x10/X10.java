package ca.tpmd.x10;

public final class X10
{

private static final char[] _hex    = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
private static final byte[] _codes  = {0x6, 0xe, 0x2, 0xa, 0x1, 0x9, 0x5, 0xd, 0x7, 0xf, 0x3, 0xb, 0x0, 0x8, 0x4, 0xc};
private static final byte[] _lookup = {'M', 'E', 'C', 'K', 'O', 'G', 'A', 'I', 'N', 'F', 'D', 'L', 'P', 'H', 'B', 'J'};

private static final int DEBUG = 3;
private static final int TIMING = 2;
private static final int VERBOSE = 1;
private static final int INFO = 0;
private static final int WARN = -1;
private static final int ERR = -2;

private static int _level;

static final void log_level(int level)
{
	String s;
	if (level > DEBUG)
		level = DEBUG;
	switch (level) {
	case DEBUG:
		s = "DEBUG";
		break;
	case TIMING:
		s = "TIMING";
		break;
	case VERBOSE:
		s = "VERBOSE";
		break;
	case INFO:
	default:
		s = "INFO";
		level = INFO;
	}
	log(INFO, "Log level: " + s);
	_level = level;
}

public static int code(int i)
{
	return _codes[i];
}

public static char house(int i)
{
	if (i < 0 || i > 15) {
		err("Invalid house code " + i);
		return 'X';
	}
	return (char)_lookup[i];
}

public static int unit(int i)
{
	return (_lookup[i] - '@');
}

public static final void debug(String s)
{
	log(DEBUG, "DEBUG\t\t" + s);
}

public static final void verbose(String s)
{
	log(VERBOSE, "VERBOSE\t" + s);
}

public static final void timing(String s)
{
	log(TIMING, "TIMING\t" + s);
}

public static final void info(String s)
{
	log(INFO, "INFO\t" + s);
}

public static final void warn(String s)
{
	log(WARN, "WARNING\t" + s);
}

public static final void err(String s)
{
	log(ERR, "ERROR\t" + s);
}

public static final void log(int l, String s)
{
	if (l <= _level)
		System.out.println(s);
}

public static final String hex(int n)
{
	n &= 0xff;
	StringBuilder result = new StringBuilder(4);
	result.append("0x");
	result.append(_hex[n >>> 4]);
	result.append(_hex[n & 0xf]);
	return result.toString();
}

public static final String hex(byte[] buf)
{
	int c;
	StringBuilder result = new StringBuilder();
	for (int i = 0; i < buf.length; i++) {
		if ((i & 0xf) == 0)
			result.append("\n");
		if ((i & 0x3) == 0)
			result.append(" ");
		c = buf[i] & 0xff;
		result.append(" ");
		result.append(_hex[c >>> 4]);
		result.append(_hex[c & 0xf]);
	}
	return result.toString();
}

public static final String hex(byte[] buf, int n)
{
	if (n <= 0)
		return "";
	int c;
	StringBuilder result = new StringBuilder();
	for (int i = 0; i < n; i++) {
		c = buf[i] & 0xff;
		result.append(" 0x");
		result.append(_hex[c >>> 4]);
		result.append(_hex[c & 0xf]);
	}
	return result.toString();
}

public static void main(String[] args)
{
	//Serial.list_ports();
	if (args.length == 0) {
		log(ERR, "Please specify serial port to use.");
		System.exit(1);
	}
	Serial comm = Serial.create(args[0]);
	log(INFO, "X10 control");
	log_level(INFO);
	if (!comm.test()) {
		log(ERR, "Could not open port " + args[0] + ", exiting...");
		System.exit(1);
	}
	log(INFO, "Interface at " + args[0] + " ready.\n");
	Thread t = new Thread(comm);
	t.start();
	Control ctrl = Control.create(comm, System.in);
	new Thread(ctrl).start();
	try {
		t.join();
	} catch (InterruptedException e) {}
	log(INFO, "\n... and we're done.");
}

}
