package ca.tpmd.x10.eeprom;

import java.util.ArrayList;
import java.util.HashMap;
import ca.tpmd.x10.X10;

public final class Macro
{

private String _name;
private int _delay;
private final ArrayList<MacroCommand> _commands;
private final int _length;
private final int _line;

private Macro(String name, int delay, ArrayList<MacroCommand> commands, int line)
{
	_name = name;
	_delay = delay;
	_commands = commands;
	_length = commands.size();
	_line = line;
}

Macro(byte[] b, String name, boolean offset)
{
	if (b.length < 2)
		throw new IllegalArgumentException("macro: got array of length " + b.length + ", expecting at least 2 byte array");
	int n = offset ? 1 : 0;
	int delay = b[n++] & 0xff;
	_delay = (delay == 0) ? 0 : (offset ? delay : -delay);
	_length = b[n++] & 0xff;
	_name = name == null ? "<unknown>" : name;
	_commands = new ArrayList<MacroCommand>(_length);
	for (int i = 0; i < _length; i++) {
		MacroCommand mc = new MacroCommand(b, n);
		_commands.add(mc);
		n += mc.size();
	}
	_line = -1;
}

static Macro parse(ArrayList<String> tokens, HashMap<String, Integer> n2o, int line)
{
	try {
		String name = Schedule.next(tokens);
		if (n2o.get(name) != null)
			throw new IllegalArgumentException("Line " + line + ": duplicate macro name: '" + name + "' already defined");
		int delay = Schedule.number(Schedule.next(tokens), -240, 240);
		ArrayList<MacroCommand> commands = new ArrayList<MacroCommand>();
		while (tokens.size() > 0) {
			MacroCommand mc = MacroCommand.parse(tokens);
			if (mc == null)
				return null;
			commands.add(mc);
		}
		return new Macro(name, delay, commands, line);
	} catch (IllegalArgumentException x) {
		X10.err("macro: " + x.getMessage());
	}
	return null;
}

void unchain()
{
	_delay = abs(_delay);
}

int offset()
{
	return _delay > 0 ? 1 : 0;
}

int line()
{
	return _line;
}

String name()
{
	return _name;
}

boolean chained()
{
	return _delay < 0;
}

public String toString()
{
	StringBuilder s = new StringBuilder();
	s.append("macro\t");
	s.append(_name);
	s.append("\t");
	s.append(_delay);
	for (int i = 0; i < _commands.size(); i++) {
		s.append("\t");
		s.append(_commands.get(i).toString());
	}
	return s.toString();
}

byte[] serialize()
{
	byte[] b = new byte[size()];
	int n = 0;
	if (_delay > 0)
		b[n++] = 0;
	b[n++] = (byte)abs(_delay);
	b[n++] = (byte)_commands.size();
	byte[] tmp;
	MacroCommand mc;
	for (int i = 0; i < _commands.size(); i++) {
		mc = _commands.get(i);
		tmp = mc.serialize();
		System.arraycopy(tmp, 0, b, n, tmp.length);
		n += tmp.length;
	}
	return b;
}

static int abs(int n)
{
	return n < 0 ? -n : n;
}

int size()
{
	int s = 2 + offset();
	int i = _commands.size();
	while (i-- > 0)
		s += _commands.get(i).size();
	return s;
}

}
