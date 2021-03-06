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

Command(Cmd c, int house, int[] units, int dim)
{
	if (units == null) {
		if (c.need_addr())
			throw new IllegalArgumentException(c.label() + ": need address");
	} else {
		if (c.x_cmd() && units.length != 1)
			throw new IllegalArgumentException(c.label() + ": need only one address");
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

int xcmd()
{
	return _command.xcmd();
}

int xdata()
{
	if (_command.args() == 0)
		return _command.xdata();
	return _dim;
}

String cmdLabel()
{
	return _command.label();
}

int cmdCode()
{
	if (_command == Cmd.PRESET_DIM)
		return _dim > 15 ? Cmd.PRESET_DIM_2.code() : Cmd.PRESET_DIM_1.code();
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

int cmdHouse()
{
	return _command == Cmd.PRESET_DIM ? X10.le(_dim & 0xf) : X10.code(_house);
}

int units()
{
	return _units;
}

int dim()
{
	return _command == Cmd.PRESET_DIM ? 1 : _dim;
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
		s.append(X10.house(cmdHouse()));
		s.append(" ");
	}
	s.append(_command.label());
	if (_command.need_dim()) {
		int i = _command == Cmd.PRESET_DIM ? 31 : 22;
		s.append(" ");
		s.append(_dim);
		s.append(" (");
		s.append(_dim * 100 / i);
		s.append("%)");
	}
	return s.toString();
}

}
