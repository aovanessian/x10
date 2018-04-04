package ca.tpmd.x10;

public final class Address
{

private final Code _house;
private final int[] _units;

public Address(Code h, int[] units)
{
	_house = h;
	if (units != null) {
		_units = new int[units.length];
		System.arraycopy(units, 0, _units, 0, units.length);
	} else
		_units = null;
}

public Code house()
{
	return _house;
}

public int[] units()
{
	return _units;
}

public boolean addressed(Code h, int unit)
{
	if (h != _house)
		return false;
	if (_units == null)
		return true;
	for (int i = 0; i < _units.length; i++)
		if (_units[i] == unit)
			return true;
	return false;
}

public String toString()
{
	StringBuilder s = new StringBuilder();
	for (int i = 0; i < _units.length; i++) {
		s.append(_house);
		s.append(_units[i]);
		s.append(" ");
	}
	return s.toString();
}

}
