package ca.tpmd.x10.eeprom;

import java.util.ArrayList;
import java.util.HashMap;
import ca.tpmd.x10.X10;

public final class Macro
{

private String _name;
private final int _delay;
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

public Macro(byte[] b, String name)
{
	if (b.length < 2)
		throw new IllegalArgumentException("macro: got array of length " + b.length + ", expecting at least 2 byte array");
	_delay = b[0] & 0xff;
	_length = b[1] & 0xff;
	_name = name == null ? "<unknown>" : name;
	_commands = new ArrayList<MacroCommand>(_length);
	int n = 2;
	for (int i = 0; i < _length; i++) {
		MacroCommand mc = new MacroCommand(b, n);
		_commands.add(mc);
		n += mc.size();
	}
	_line = -1;
}

public static Macro parse(ArrayList<String> tokens, HashMap<String, Integer> n2o, int line)
{
	try {
		String name = Schedule.next(tokens);
		if (n2o.get(name) != null)
			throw new IllegalArgumentException("Line " + line + ": duplicate macro name: '" + name + "' already defined");
		int delay = Schedule.number(Schedule.next(tokens), 0, 240);
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

int line()
{
	return _line;
}

public String name()
{
	return _name;
}

public boolean chained()
{
	return _delay != 0;
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

public byte[] serialize()
{
	byte[] b = new byte[size()];
	b[0] = (byte)_delay;
	b[1] = (byte)_commands.size();
	byte[] tmp;
	MacroCommand mc;
	int n = 2;
	for (int i = 0; i < _commands.size(); i++) {
		mc = _commands.get(i);
		tmp = mc.serialize();
		System.arraycopy(tmp, 0, b, n, tmp.length);
		n += tmp.length;
	}
	return b;
}

public int size()
{
	int s = 2;
	int i = _commands.size();
	while (i-- > 0)
		s += _commands.get(i).size();
	return s;
}

}
