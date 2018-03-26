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
	if (c.need_addr())
		throw new IllegalArgumentException(c.label() + ": need address");
	if (c.need_dim())
		throw new IllegalArgumentException(c.label() + ": need dim level");
}

public Command(Cmd c, Code house, int unit)
{
	this(c, house, unit, 0);
	if (c.need_dim())
		throw new IllegalArgumentException(c.label() + ": need dim level");
}

public Command(Cmd c, Code house, int unit, int dim)
{
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

public Command(Cmd c, Code house, int[] units, int dim)
{
	_command = c;
	_house = house;
	_units = c.need_addr() ? units : null;
	_dim = c.need_dim() ? dim : 1;
	if (!c.need_addr() && units != null)
		X10.info(c.label() + ": superfluous address");
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

}
