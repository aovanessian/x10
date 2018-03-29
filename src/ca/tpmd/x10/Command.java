package ca.tpmd.x10;

public class Command
{

private final Code _house;
private final Cmd _command;
private final int[] _units;
private final int _dim;

public Command(Cmd c, Code house)
{
	this(c, house, null, 0);
}

public Command(Cmd c, Code house, int unit)
{
	this(c, house, unit, 0);
	if (c.need_dim())
		throw new IllegalArgumentException(c.label() + ": need dim level");
}

public Command(Cmd c, Code house, int unit, int dim)
{
	if (unit < 1 || unit > 16)
		throw new IllegalArgumentException("Unit id outside allowed range: " + unit);
	if (dim < 0 || dim > 22)
		throw new IllegalArgumentException("Dim level outside allowed range: " + dim);
	int[] units = {unit};
	_command = c;
	_house = house;
	_units = c.need_addr() ? units : null;
	_dim = c.need_dim() ? dim : 1;
	if (!c.need_addr())
		X10.info(c.label() + ": superfluous address");
	if (!c.need_dim() && dim != 0)
		X10.info(c.label() + ": superfluous dim level");

}

public Command(Cmd c, Code house, int[] units)
{
	this(c, house, units, 0);
	if (c.need_dim())
		throw new IllegalArgumentException(c.label() + ": need dim level");
}

public Command(Cmd c, Code house, int[] units, int dim)
{
	if (c.need_addr()) {
		if (units == null)
			throw new IllegalArgumentException(c.label() + ": need address");
		for (int i = 0; i < units.length; i++)
			if (units[i] < 1 || units[i] > 16)
				throw new IllegalArgumentException("Unit id outside allowed range: " + units[i]);
	} else if (units != null) {
		X10.info(c.label() + ": superfluous address");
	}

	if (dim < 0 || dim > 22)
		throw new IllegalArgumentException("Dim level outside allowed range: " + dim);
	_command = c;
	_house = house;
	_units = c.need_addr() ? units : null;
	_dim = c.need_dim() ? dim : 1;
	if (!c.need_dim() && dim != 0)
		X10.info(c.label() + ": superfluous dim level");
}

public String cmdLabel()
{
	return _command.label();
}

public int cmdCode()
{
	return _command.ordinal();
}

public boolean cmdSystem()
{
	return _command.sys_cmd();
}

public boolean exit()
{
	return _command == Cmd.EXIT;
}

public int houseCode()
{
	return _house.ordinal();
}

public int[] units()
{
	return _units;
}

public int dim()
{
	return _dim;
}

public String toString()
{
	StringBuilder s = new StringBuilder();
	if (_units != null) {
		for (int i = 0; i < _units.length; i++) {
			s.append(_house);
			s.append(_units[i]);
			s.append(" ");
		}
	} else if (_house != null) {
		s.append(_house);
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
