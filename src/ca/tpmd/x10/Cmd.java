package ca.tpmd.x10;

enum Cmd
{
// 			addr	label
ALL_OFF			(false,	"all units off"),
LIGHTS_ON		(false,	"all lights on"),
ON			(false,	"on"),
OFF			(false,	"off"),
DIM			(false,	"dim"),
BRIGHT			(false,	"brighten"),
LIGHTS_OFF		(false,	"all lights off"),
EXT_CODE_1		(true,	"extended code"),
HAIL_REQ		(false,	"hail request"),
HAIL_ACK		(false,	"hail acknowledge"),
EXT_CODE_3		(true,	"extended code 3"),
UNUSED			(false,	"unused"),
EXT_CODE_2		(true,	"extended code 2"),
STATUS_ON		(false,	"module on"),
STATUS_OFF		(false,	"module off"),
STATUS_REQ		(false,	"status request"),
ADDRESS			(true,  "address unit(s)"),
SYSTEM_STATE		(false,	"system state"),
RING_ENABLE		(false, "enable serial ring"),
RING_DISABLE		(false, "disable serial ring"),
CLOCK_SET		(false, "set interface clock"),
LOG_LEVEL		(false,	"log level"),
EXIT			(false,	"exit");

private final boolean addr;
private final String label;
private static final Cmd[] values = values();

Cmd(boolean a, String l)
{
	addr = a;
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
	return addr;
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
