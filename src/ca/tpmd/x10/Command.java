package ca.tpmd.x10;

public final class Command
{

private final Cmd _command;
private final int _dim;
private final int _house;
private final int _units;

private byte[] _data = null;
private int _xdata;

public Command(Cmd c)
{
	this(c, -1, null, 0);
}

public Command(Cmd c, int house, int unit, int xcmd, int xdata)
{
	if (!c.x_cmd())
		throw new IllegalArgumentException("not an extended command: " + c.label());
	if (unit < 1 || unit > 16)
		throw new IllegalArgumentException("Unit id outside allowed range: " + unit);
	_command = c;
	_house = house;
	_units = 1 << X10.code(unit - 1);
	_dim = xcmd;
	_xdata = xdata;
}

Command(Cmd c, int house, int[] units, int dim)
{
	if (c.need_addr()) {
		if (units == null)
			throw new IllegalArgumentException(c.label() + ": need address");
		for (int i = 0; i < units.length; i++)
			if (units[i] < 1 || units[i] > 16)
				throw new IllegalArgumentException("Unit id outside allowed range: " + units[i]);
	}
	_units = mask(units);
	_command = c;
	_house = house;
	_dim = c.need_dim() ? dim : 1;
}

private static int mask(int[] units)
{
	if (units == null)
		return 0;
	int m = 0;
	for (int i = 0; i < units.length; i++)
		m |= 1 << X10.code(units[i] - 1);
	return m;
}

public void setData(byte[] d)
{
	_data = new byte[d.length];
	System.arraycopy(d, 0, _data, 0, d.length);
}

byte[] getData()
{
	return _data;
}

Cmd cmd()
{
	return _command;
}

String cmdLabel()
{
	return _command.label();
}

int cmdCode()
{
	return _command.code();
}

boolean cmdSystem()
{
	return _command.sys_cmd();
}

boolean cmdExtended()
{
	return _command.x_cmd();
}

boolean exit()
{
	return _command == Cmd.EXIT;
}

int house()
{
	return _house;
}

int units()
{
	return _units;
}

int dim()
{
	return _dim;
}

public String toString()
{
	StringBuilder s = new StringBuilder();
	if (_units != 0) {
		for (int i = 0; i < 16; i++) {
			if ((_units & (1 << X10.code(i))) == 0)
				continue;
			s.append((char)(_house + 'A'));
			s.append(i + 1);
			s.append(" ");
		}
	} else if (_house != -1) {
		s.append((char)(_house + 'A'));
		s.append(" ");
	}
	s.append(_command.label());
	if (_command.need_dim()) {
		s.append(" ");
		s.append(_dim * 100 / 22);
		s.append("%");
	}
	return s.toString();
}

}
