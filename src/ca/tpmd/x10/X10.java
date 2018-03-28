package ca.tpmd.x10;

public class X10
{

private static final char[] _hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

private static final int DEBUG = 7;
private static final int VERBOSE = 6;
private static final int TIMING = 5;
private static final int INFO = 4;
private static final int WARN = 3;
private static final int ERR = 2;

private static int _level = VERBOSE;

public static final void debug(String s)
{
	log(DEBUG, "DEBUG\t" + s);
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
        int c = n & 0xff;
        StringBuilder result = new StringBuilder(4);
        result.append("0x");
        result.append(_hex[c >>> 4]);
        result.append(_hex[c & 0xf]);
        return result.toString();
}

public static final String hex(byte[] buf, int n)
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

public static void main(String[] args)
{
	Serial.list_ports();
	Serial comm = Serial.create(args[0]);
	comm.setup();
	Thread t = new Thread(comm);
	t.start();
	try {
		t.join();
	} catch (InterruptedException e) {
		e.printStackTrace();
	}
	comm.teardown();
}

}
