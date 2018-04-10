package ca.tpmd.x10;

final class Command
{

private final Cmd _command;
private final int _dim;
private final int _house;
private final int[] _units;

Command(Cmd c)
{
	this(c, -1, null, 0);
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

	if (dim < 0 || dim > 22)
		throw new IllegalArgumentException("Dim level outside allowed range: " + dim);
	_command = c;
	_house = house;
	_units = units;
	_dim = c.need_dim() ? dim : 1;
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
	return _command.ordinal();
}

boolean cmdSystem()
{
	return _command.sys_cmd();
}

boolean exit()
{
	return _command == Cmd.EXIT;
}

int house()
{
	return _house;
}

int[] units()
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
	if (_units != null) {
		for (int i = 0; i < _units.length; i++) {
			s.append((char)(_house + 'A'));
			s.append(_units[i]);
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
