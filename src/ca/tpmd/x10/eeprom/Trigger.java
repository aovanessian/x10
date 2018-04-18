package ca.tpmd.x10.eeprom;

import java.util.ArrayList;
import java.util.HashMap;
import ca.tpmd.x10.X10;
import ca.tpmd.x10.Cmd;
import ca.tpmd.x10.Control;

public final class Trigger
{

private static final int SIZE = 3;

private final int _house;
private final int _unit;
private final boolean _on;
private final String _macro_name;
private final int _line;
private int _ptr = -1;

private Trigger(int h, int u, boolean on, String macro, int line)
{
	_house = h;
	_unit = u;
	_on = on;
	_macro_name = macro;
	_line = line;
}

public Trigger(byte[] b, HashMap<Integer, String> o2n)
{
	if (b.length != SIZE)
		throw new IllegalArgumentException("trigger: got array of length " + b.length + ", expecting a " + SIZE + " byte array");
	int hc = b[0] & 0xff;
	_house = X10.house(hc >>> 4);
	_unit = X10.unit(hc & 0xf);
	_on = (b[1] & 0x80) != 0;
	_ptr = (b[2] & 0xff) | ((b[1] & 0x3) << 8);
	_macro_name = (o2n == null) ? null : o2n.get(_ptr);
	_line = -1;
}

public byte[] serialize()
{
	if (_ptr == -1) {
		X10.err("trigger: macro pointer not set");
		return null;
	}
	byte[] r = new byte[SIZE];
	r[0] = (byte)((X10.code(_house - 'A') << 4) | X10.code(_unit - 1));
	r[1] = (byte)((_on ? 0x80 : 0x50) | (_ptr >>> 8));
	r[2] = (byte)(_ptr & 0xff);
	return r;
}

int line()
{
	return _line;
}

public String macro()
{
	return _macro_name;
}

public int pointer()
{
	return _ptr;
}

public void pointer(int p)
{
	_ptr = p;
}

public static Trigger parse(ArrayList<String> tokens, int line)
{
	if (tokens.size() < 4) {
		X10.err("Line " + line + ": not enough parameters to create trigger");
		return null;
	}
	try {
		String t = Schedule.next(tokens);
		Cmd cmd = Control.command(t);
		if (cmd == null || !(cmd == Cmd.ON || cmd == Cmd.OFF)) {
			X10.err("invalid trigger command '" + t + "', should be either ON or OFF");
			return null;
		}
		boolean on = cmd == Cmd.ON;
		int house = Schedule.house(Schedule.next(tokens));
		int unit = Schedule.number(Schedule.next(tokens), 1, 16);
		String macro = Schedule.next(tokens);
		X10.debug("house " + house + ", unit " + unit + ", command " + (on ? "ON" : "OFF") + ", macro: " + macro);
		return new Trigger(house, unit, on, macro, line);
	} catch (IllegalArgumentException x) {
		X10.err("trigger: " + x.getMessage());
	}
	return null;
}

public String toString()
{
	StringBuilder s = new StringBuilder();
	s.append("trigger\t");
	s.append(_on ? "ON " : "OFF ");
	s.append((char)_house);
	s.append(" ");
	s.append(_unit);
	s.append("\t");
	s.append(_macro_name);
	s.append("\t# offset: ");
	s.append(_ptr);
	return s.toString();
}

}
