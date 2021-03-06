package ca.tpmd.x10.eeprom;

import java.util.ArrayList;
import java.util.Locale;
import ca.tpmd.x10.X10;
import ca.tpmd.x10.Cmd;
import ca.tpmd.x10.Control;

final class MacroCommand
{

private final Cmd _cmd;
private final int _house;
private final int _units;
private final int _data; // also used for dim level
private final int _command;

private MacroCommand(Cmd cmd, int house, ArrayList<Integer> units, int data, int command)
{
	_cmd = cmd;
	_house = house;
	_units = addr_mask(units);
	_data = data;
	_command = command;
}

MacroCommand(byte[] b, int n)
{
	int command, data;
	int hc = b[n] & 0xff;
	_house = X10.house(hc >>> 4);
	_cmd = Cmd.lookup(hc & 0xf);
	command = data = 0;
	_units = ((b[n + 1] & 0xff) << 8) | (b[n + 2] & 0xff);
	switch (_cmd.x10_data_len()) {
	case 2:
		command = b[n + 4] & 0xff;
	case 1:
		data = b[n + 3] & 0xff;
	}
	_command = command;
	_data = data;
}

byte[] serialize()
{
	byte[] b = new byte[3 + _cmd.x10_data_len()];
	b[0] = (byte)((X10.code(_house - 'A') << 4) | _cmd.code());
	b[1] = (byte)((_units >>> 8) & 0xff);
	b[2] = (byte)(_units & 0xff);
	switch (_cmd.x10_data_len()) {
	case 3:
		b[1] = b[2] = 0;
		b[3] = 0; // unit, actually
		b[4] = (byte)_data;
		b[5] = (byte)_command;
		break;
	case 1:
		b[3] = (byte)(_data & 0xff);
	}
	return b;
}

private static ArrayList<Integer> units(ArrayList<String> tokens)
{
	ArrayList<Integer> units = new ArrayList<Integer>();
	while (tokens.size() != 0) {
		try {
			units.add(Schedule.number(tokens.get(0), 1, 16));
		} catch (IllegalArgumentException x) {
			break;
		}
		tokens.remove(0);
	}
	return units;
}

static MacroCommand parse(ArrayList<String> tokens)
{
	try {
		String t = Schedule.next(tokens);
		Cmd cmd = Control.command(t);
		if (cmd == null || !cmd.x10_cmd()) {
			X10.err("invalid eeprom macro command " + t);
			return null;
		}
		int command = 0;
		int data = 0;
		if (cmd.need_dim()) {
			data = Schedule.number(Schedule.next(tokens), -31, 31);
			if (data < 0) {
				data = -data;
				if (cmd == Cmd.DIM)
					data |= 0x80;
			}
		}
		int house = Schedule.house(Schedule.next(tokens));
		ArrayList<Integer> units = units(tokens);
		return new MacroCommand(cmd, house, units, data, command);
	} catch (IllegalArgumentException x) {
		X10.err("macro command: " + x.getMessage());
	}
	return null;
}

private final boolean bright_before_dim()
{
	return _cmd == Cmd.DIM && ((_data & 0x80) != 0);
}

public String toString()
{
	StringBuilder s = new StringBuilder();
	s.append(_cmd);
	s.append(" ");
	switch (_cmd.x10_data_len()) {
	case 1:
		if (bright_before_dim())
			s.append("-");
		s.append(_data & 0x7f);
		s.append(" ");
		break;
	case 2:
		s.append(_data);
		s.append(" ");
		s.append(_command);
		s.append(" ");
		break;
	}
	s.append((char)_house);
	for (int i = 0; i < 16; i++) {
		if ((_units & (1 << X10.code(i))) != 0) {
			s.append(" ");
			s.append(i + 1);
		}
	}
	return s.toString();
}

private static int addr_mask(ArrayList<Integer> units)
{
	if (units == null)
		return 0;
	int m = 0;
	for (int i = 0; i < units.size(); i++)
		m |= 1 << X10.code(units.get(i) - 1);
	return m;
}

int size()
{
	return 3 + _cmd.x10_data_len();
}

}
