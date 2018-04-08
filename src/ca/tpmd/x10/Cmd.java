package ca.tpmd.x10;

enum Cmd
{
// 			addr	label
ALL_OFF			(false,	"all units off"),
LIGHTS_ON		(false,	"all lights on"),
ON			(true,	"on"),
OFF			(true,	"off"),
DIM			(true,	"dim"),
BRIGHT			(true,	"brighten"),
LIGHTS_OFF		(false,	"all lights off"),
EXT_CODE_1		(true,	"extended code"),
HAIL_REQ		(false,	"hail request"),
HAIL_ACK		(false,	"hail acknowledge"),
EXT_CODE_3		(true,	"extended code 3"),
UNUSED			(false,	"unused"),
EXT_CODE_2		(true,	"extended code 2"),
STATUS_ON		(true,	"module on"),
STATUS_OFF		(true,	"module off"),
STATUS_REQ		(true,	"status request"),
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
	return this.ordinal() > 15;
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
