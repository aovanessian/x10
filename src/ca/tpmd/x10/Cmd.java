package ca.tpmd.x10;

enum Cmd
{
// 			addr	response length	label
ALL_OFF			(false,	0,		"all units off"),
LIGHTS_ON		(false,	0,		"all lights on"),
ON			(true,	0,		"on"),
OFF			(true,	0,		"off"),
DIM			(true,	1,		"dim"),
BRIGHT			(true,	1,		"brighten"),
LIGHTS_OFF		(false,	0,		"all lights off"),
EXT_CODE		(true,	2,		"extended code"),
HAIL_REQ		(true,	0,		"hail request"),
HAIL_ACK		(true,	0,		"hail acknowledge"),
PRESET_DIM_1		(true,	0,		"pre-set dim 1"),
PRESET_DIM_2		(true,	0,		"pre-set dim 2"),
EXT_DATA_TFR		(true,	0,		"extended data transfer"),
STATUS_ON		(true,	0,		"status on"),
STATUS_OFF		(true,	0,		"status off"),
STATUS_REQ		(true,	1,		"status request"),
SYSTEM_STATE		(false,	0,		"system state"),
EXIT			(false,	0,		"exit");

private final boolean addr;
private final int response_len;
private final String label;
private static final Cmd[] values = values();

Cmd(boolean a, int res, String l)
{
	addr = a;
	response_len = res;
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

int status_len()
{
	return response_len;
}

String label()
{
	return label;
}

}
