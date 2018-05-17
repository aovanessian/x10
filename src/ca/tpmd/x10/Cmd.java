package ca.tpmd.x10;

public enum Cmd
{
//			label
ALL_OFF			("all units off"),
LIGHTS_ON		("all lights on"),
ON			("on"),
OFF			("off"),
DIM			("dim", -1, 0),
BRIGHT			("brighten", -1, 0),
LIGHTS_OFF		("all lights off"),
EXT_CODE_1		("extended code", -2, 0),
HAIL_REQ		("hail request"),
HAIL_ACK		("hail acknowledge"),
PRESET_DIM_1		("preset dim 1"),
PRESET_DIM_2		("preset dim 2"),
EXT_CODE_2		("extended code 2"),
STATUS_ON		("module on"),
STATUS_OFF		("module off"),
STATUS_REQ		("status request"),

ADDRESS			("address unit(s)"),
PRESET_DIM		("preset dim"),

XON			("extended on", 0x3e, 0x31),
XOFF			("extended off", 0x00, 0x31),
XDIM			("extended preset", -1, 0x31),
X_ALL_ON		("extended all units on", 0x00, 0x33),
X_ALL_OFF		("extended all units off", 0x00, 0x34),

SYSTEM_STATE		("system state"),
RING_ENABLE		("enable serial ring"),
RING_DISABLE		("disable serial ring"),
EEPROM_ERASE		("erase eeprom"),
EEPROM_WRITE		("write eeprom"),
CLOCK_SET		("set interface clock"),
RESET			("reset interface"),
LOG_LEVEL		("log level"),
SCHEDULE		("schedule"),
EXIT			("exit");

private final String label;
private static final Cmd[] values = values();
private final int xcmd;
private final int xdata;

private Cmd(String l)
{
	xcmd = xdata = 0;
	label = l;
}

private Cmd(String l, int d, int c)
{
	xcmd = c;
	xdata = d;
	label = l;
}

public int args()
{
	return (xdata < 0) ? -xdata : 0;
}

public static Cmd lookup(int n)
{
	return values[n];
}

public int code()
{
	switch (this) {
	case XON:
	case XOFF:
	case XDIM:
	case X_ALL_OFF:
	case X_ALL_ON:
		return EXT_CODE_1.ordinal();
	case PRESET_DIM:
		return PRESET_DIM_1.ordinal();
	}
	return ordinal();
}

public boolean x10_cmd()
{
	switch (this) {
	case ALL_OFF:
	case LIGHTS_ON:
	case ON:
	case OFF:
	case DIM:
	case BRIGHT:
	case LIGHTS_OFF:
	case EXT_CODE_1:
	case HAIL_REQ:
	case PRESET_DIM_1:
	case PRESET_DIM_2:
	case EXT_CODE_2:
	case STATUS_REQ:
		return true;
	}
	return false;
}

public int x10_data_len()
{
	switch (this) {
	case DIM:
	case BRIGHT:
		return 1;
	case EXT_CODE_1:
		return 3;
	}
	return 0;
}

boolean x_cmd()
{
	switch (this) {
	case EXT_CODE_1:
	case XON:
	case XOFF:
	case XDIM:
	case X_ALL_ON:
	case X_ALL_OFF:
		return true;
	}
	return false;
}

boolean sys_cmd()
{
	return code() > 16;
}

boolean need_addr()
{
	return this == ADDRESS;
}

public boolean need_dim()
{
	return this == DIM || this == BRIGHT || this == PRESET_DIM;
}

String label()
{
	return label;
}

}
