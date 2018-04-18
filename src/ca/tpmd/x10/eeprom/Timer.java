package ca.tpmd.x10.eeprom;

import java.util.ArrayList;
import java.util.HashMap;
import ca.tpmd.x10.X10;

public final class Timer
{

private final int _wd_mask;
private final int _day_start;
private final int _day_end;
private final int _time_start;
private final int _time_end;
private final boolean _rnd_start;
private final boolean _rnd_end;
private int _ptr_start;
private int _ptr_end;
private String _macro_start;
private String _macro_end;
private final int _line;

private static final char[] WD = {'s', 'm', 't', 'w', 't', 'f', 's'};
private static final int SIZE = 9;

private Timer(int wd_mask, int start_day, int start_time, boolean start_rnd, int end_day, int end_time, boolean end_rnd, String macro_start, String macro_end, int line)
{
	_wd_mask = wd_mask;
	_day_start = start_day;
	_day_end = end_day;
	_time_start = start_time;
	_time_end = end_time;
	_rnd_start = start_rnd;
	_rnd_end = end_rnd;
	_macro_start = macro_start;
	_macro_end = macro_end;
	_ptr_start = _ptr_end = -1;
	_line = line;
}

public Timer(byte[] b, HashMap<Integer, String> o2n)
{
	if (b.length != SIZE)
		throw new IllegalArgumentException("timer: got array of length " + b.length + ", expecting a " + SIZE + " byte array");
	if ((b[0] & 0xff) == 0xff)
		throw new IllegalArgumentException("timer: end marker reached");
	_wd_mask	= b[0] & 0xff;
	_day_start	= ((b[4] & 0x80) << 1) | (b[1] & 0xff);
	_day_end	= ((b[5] & 0x80) << 1) | (b[2] & 0xff);
	_time_start 	= (b[4] & 0x7f) + ((b[3] & 0xf0) >>> 4) * 120;
	_time_end	= (b[5] & 0x7f) + (b[3] & 0xf) * 120;
	_rnd_start	= (b[6] & 0x80) != 0;
	_rnd_end	= (b[6] & 0x8) != 0;
	_ptr_start	= (b[7] & 0xff) | ((b[6] & 0x30) << 4);
	_ptr_end	= (b[8] & 0xff) | ((b[6] & 0x3) << 8);
	if (o2n != null) {
		_macro_start = o2n.get(_ptr_start);
		_macro_end = o2n.get(_ptr_end);
	}
	_line = -1;
}

int line()
{
	return _line;
}

public void pointers(int start, int end)
{
	_ptr_start = start;
	_ptr_end = end;
}

public int pointer()
{
	return _ptr_start < _ptr_end ? _ptr_start : _ptr_end;
}

public int ptr_start()
{
	return _ptr_start;
}

public int ptr_end()
{
	return _ptr_end;
}

public String macro_start()
{
	return _macro_start;
}

public String macro_end()
{
	return _macro_end;
}

public byte[] serialize()
{
	if (_ptr_start == -1 || _ptr_end == -1) {
		X10.err("timer " + toString() + ": macro pointers not set");
		return null;
	}
	byte[] r = new byte[SIZE];
	int sh = _time_start / 120;
	int eh = _time_end / 120;
	r[0] = (byte)_wd_mask;
	r[1] = (byte)(_day_start & 0xff);
	r[2] = (byte)(_day_end & 0xff);
	r[3] = (byte)((sh << 4) | eh);
	r[4] = (byte)((_day_start >>> 1 & 0x80) | ((_time_start - sh * 120) & 0x7f));
	r[5] = (byte)((_day_end >>> 1 & 0x80) | ((_time_end - eh * 120) & 0x7f));
	r[6] = (byte)((_rnd_start ? 0x80 : 0) | ((_ptr_start & 0x300) >>> 4) | (_rnd_end ? 0x8 : 0) | ((_ptr_end & 0x300) >>> 8));
	r[7] = (byte)(_ptr_start & 0xff);
	r[8] = (byte)(_ptr_end & 0xff);
	return r;
}

private static int wd_mask(String wd)
{
	X10.debug("parsing weekday mask: " + wd);
	if (wd == null || wd.length() != 7)
		throw new IllegalArgumentException("Incorrect weekday mask: " + wd);
	int i = 7;
	int mask = 0;
	while (i-- > 0)
		if (wd.charAt(i) != '.')
			mask |= 1 << i;
	X10.debug("mask is " + X10.hex(mask));
	if (mask == 0)
		throw new IllegalArgumentException("Timer will never trigger: " + wd);
	return mask;
}

private static String wd(int wd_mask)
{
	StringBuilder s = new StringBuilder(7);
	for (int i = 0; i < 7; i++)
		s.append((wd_mask & (1 << i)) == 0 ? '.' : WD[i]);
	return s.toString();
}

private static int date(String date)
{
	X10.debug("parsing date: " + date);
	int day = Integer.parseInt(date);
	return day;
}

private static String date(int date)
{
	return "" + date;
}

private static int time(String time)
{
	X10.debug("parsing time: " + time);
	if (time == null || time.length() < 3 || time.length() > 6)
		throw new IllegalArgumentException("Incorrect time format: " + time);
	byte[] b = time.getBytes();
	int[] z = new int[2];
	boolean rnd = false;
	int n = 0;
	for (int i = 0; i < b.length; i++) {
		switch (b[i]) {
		case 'r':
		case 'R':
			rnd = true;
			break;
		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9':
			z[n] = z[n] * 10 + b[i] - '0';
			break;
		case ':':
			if (i == 0 || n > 0)
				throw new IllegalArgumentException("1Incorrect time format: " + time);
			n++;
			break;
		default:
			throw new IllegalArgumentException("2Incorrect time format: " + time);
		}
	}
	if (z[0] > 23 || z[1] > 59)
		throw new IllegalArgumentException("3Incorrect time format: " + time);
	n = z[0] * 60 + z[1];
	if (n >= 24 * 60)
		throw new IllegalArgumentException("4Incorrect time format: " + time);
	return rnd ? -n : n;
}

private static String time(int time, boolean rnd)
{
	int h = time / 60;
	StringBuilder s = new StringBuilder(5);
	s.append(pad(h));
	s.append(":");
	s.append(pad(time - h * 60));
	if (rnd)
		s.append("r");
	return s.toString();
}

public static Timer parse(ArrayList<String> tokens, int line)
{
	boolean rnd_start = false;
	boolean rnd_end = false;
	if (tokens.size() != 7) {
		X10.err("Line " + line + ": not enough parameters to create timer");
		return null;
	}
	try {
		int wd_mask = wd_mask(Schedule.next(tokens));
		int day_start = date(Schedule.next(tokens));
		int day_end = date(Schedule.next(tokens));
		int time_start = time(Schedule.next(tokens));
		if (time_start < 0) {
			time_start = -time_start;
			rnd_start = true;
		}
		int time_end = time(Schedule.next(tokens));
		if (time_end < 0) {
			time_end = -time_end;
			rnd_end = true;
		}
		String macro_start = Schedule.next(tokens);
		String macro_end = Schedule.next(tokens);
		X10.debug("weekday mask: " + wd_mask + ", start day: " + day_start + ", end day: " + day_end + 
				", start time: " + time_start + (rnd_start ? " +60m" : "") + ", end time: " + time_end + (rnd_end ? " +60m" : ""));
		Timer t = new Timer(wd_mask, day_start, time_start, rnd_start, day_end, time_end, rnd_end, macro_start, macro_end, line);
		return t;
	} catch (IllegalArgumentException x) {
		X10.err(x.getMessage());
	}
	return null;
} 

private static String pad(int i)
{
	return (i < 10) ? "0" + i : "" + i;
}

public String toString()
{
	StringBuilder s = new StringBuilder();
	s.append("timer\t");
	s.append(wd(_wd_mask));
	s.append("\t");
	s.append(date(_day_start));
	s.append(" ");
	s.append(date(_day_end));
	s.append("\t");
	s.append(time(_time_start, _rnd_start));
	s.append(" ");
	s.append(time(_time_end, _rnd_end));
	s.append("\t");
	s.append(_macro_start);
	s.append(" ");
	s.append(_macro_end);
	if (_ptr_start != 0 && _ptr_end != 0) {
		s.append("\t# start macro offset: ");
		s.append(_ptr_start);
		s.append(", end macro offset: ");
		s.append(_ptr_end);
	}
	return s.toString();
}

}
