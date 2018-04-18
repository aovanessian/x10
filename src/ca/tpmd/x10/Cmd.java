package ca.tpmd.x10;

public enum Cmd
{
//			eeprom	label
ALL_OFF			(true,	"all units off"),
LIGHTS_ON		(true,	"all lights on"),
ON			(true,	"on"),
OFF			(true,	"off"),
DIM			(true,	"dim"),
BRIGHT			(true,	"brighten"),
LIGHTS_OFF		(true,	"all lights off"),
EXT_CODE_1		(true,	"extended code"),
HAIL_REQ		(true,	"hail request"),
HAIL_ACK		(false,	"hail acknowledge"),
EXT_CODE_3		(true,	"extended code 3"),
UNUSED			(false,	"unused"),
EXT_CODE_2		(true,	"extended code 2"),
STATUS_ON		(false,	"module on"),
STATUS_OFF		(false,	"module off"),
STATUS_REQ		(false,	"status request"),

ADDRESS			(false,	"address unit(s)"),
SYSTEM_STATE		(false,	"system state"),
RING_ENABLE		(false,	"enable serial ring"),
RING_DISABLE		(false,	"disable serial ring"),
EEPROM_ERASE		(false,	"erase eeprom"),
EEPROM_WRITE		(false, "write eeprom"),
CLOCK_SET		(false,	"set interface clock"),
LOG_LEVEL		(false,	"log level"),
SCHEDULE		(false,	"schedule"),
EXIT			(false,	"exit");

private final String label;
private final boolean eeprom;
private static final Cmd[] values = values();

private Cmd(boolean ee, String l)
{
	eeprom = ee;
	label = l;
}

public static Cmd lookup(int n)
{
	return values[n];
}

public boolean x10_cmd()
{
	return eeprom;
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

public boolean x10_need_addr()
{
	switch (this) {
	case ALL_OFF:
	case LIGHTS_ON:
	case LIGHTS_OFF:
	case EXT_CODE_3:
	case EXT_CODE_2:
		return false;
	}
	return false;
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
