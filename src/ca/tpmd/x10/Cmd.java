package ca.tpmd.x10;

public enum Cmd
{
//			label
ALL_OFF			("all units off"),
LIGHTS_ON		("all lights on"),
ON			("on"),
OFF			("off"),
DIM			("dim"),
BRIGHT			("brighten"),
LIGHTS_OFF		("all lights off"),
EXT_CODE_1		("extended code"),
HAIL_REQ		("hail request"),
HAIL_ACK		("hail acknowledge"),
EXT_CODE_3		("extended code 3"),
UNUSED			("unused"),
EXT_CODE_2		("extended code 2"),
STATUS_ON		("module on"),
STATUS_OFF		("module off"),
STATUS_REQ		("status request"),

ADDRESS			("address unit(s)"),
SYSTEM_STATE		("system state"),
RING_ENABLE		("enable serial ring"),
RING_DISABLE		("disable serial ring"),
EEPROM_ERASE		("erase eeprom"),
EEPROM_WRITE		("write eeprom"),
CLOCK_SET		("set interface clock"),
LOG_LEVEL		("log level"),
SCHEDULE		("schedule"),
EXIT			("exit");

private final String label;
private static final Cmd[] values = values();

private Cmd(String l)
{
	label = l;
}

public static Cmd lookup(int n)
{
	return values[n];
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
	case EXT_CODE_3:
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
	case HAIL_REQ:
	case EXT_CODE_3:
	case EXT_CODE_2:
		return 3;
	}
	return 0;
}

boolean sys_cmd()
{
	return ordinal() > 16;
}

boolean need_addr()
{
	return this == ADDRESS;
}

public boolean need_dim()
{
	return this == DIM || this == BRIGHT;
}

String label()
{
	return label;
}

}
