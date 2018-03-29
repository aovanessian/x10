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
EXT_CODE		(true,	"extended code"),
HAIL_REQ		(true,	"hail request"),
HAIL_ACK		(true,	"hail acknowledge"),
PRESET_DIM_1		(true,	"pre-set dim 1"),
PRESET_DIM_2		(true,	"pre-set dim 2"),
EXT_DATA_TFR		(true,	"extended data transfer"),
STATUS_ON		(true,	"status on"),
STATUS_OFF		(true,	"status off"),
STATUS_REQ		(true,	"status request"),
SYSTEM_STATE		(false,	"system state"),
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
	return this == SYSTEM_STATE || this == EXIT;
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
