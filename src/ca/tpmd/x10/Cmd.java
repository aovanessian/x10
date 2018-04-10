package ca.tpmd.x10;

enum Cmd
{

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
ADDRESS			( "address unit(s)"),
SYSTEM_STATE		("system state"),
RING_ENABLE		("enable serial ring"),
RING_DISABLE		("disable serial ring"),
MACROS_ERASE		("erase eeprom macros"),
CLOCK_SET		("set interface clock"),
LOG_LEVEL		("log level"),
EXIT			("exit");

private final String label;
private static final Cmd[] values = values();

Cmd(String l)
{
	label = l;
}

static Cmd lookup(int n)
{
	return values[n];
}

boolean sys_cmd()
{
	return this.ordinal() > 16;
}

boolean need_addr()
{
	return this == ADDRESS;
}

boolean need_dim()
{
	return this == DIM || this == BRIGHT;
}

String label()
{
	return label;
}

}
