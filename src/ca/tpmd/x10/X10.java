package ca.tpmd.x10;

public class X10
{

private static final int DEBUG = 7;
private static final int VERBOSE = 6;
private static final int TIMING = 5;
private static final int INFO = 4;
private static final int WARN = 3;
private static final int ERR = 2;
private static int _level = INFO;

public static final void debug(String s)
{
	log(DEBUG, s);
}

public static final void verbose(String s)
{
	log(VERBOSE, s);
}

public static final void timing(String s)
{
	log(TIMING, s);
}

public static final void info(String s)
{
	log(INFO, s);
}

public static final void warn(String s)
{
	log(WARN, s);
}

public static final void err(String s)
{
	log(ERR, s);
}

public static final void log(int l, String s)
{
	if (l <= _level)
		System.out.println(s);
}

}
