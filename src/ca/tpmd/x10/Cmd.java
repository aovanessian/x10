package ca.tpmd.x10;

enum Cmd
{
// 			addr	dim	response length	label
ALL_OFF			(false,	false,	0,		"all units off"),
LIGHTS_ON		(false,	false,	0,		"all lights on"),
ON			(true,	false,	0,		"on"),
OFF			(true,	false,	0,		"off"),
DIM			(true,	true,	1,		"dim"),
BRIGHT			(true,	true,	1,		"brighten"),
LIGHTS_OFF		(false, false,	0,		"all lights off"),
EXT_CODE		(true,	false,	2,		"extended code"),
HAIL_REQ		(true,	false,	0,		"hail request"),
HAIL_ACK		(true,	false,	0,		"hail acknowledge"),
PRESET_DIM_1		(true,	true,	0,		"pre-set dim 1"),
PRESET_DIM_2		(true,	true,	0,		"pre-set dim 2"),
EXT_DATA_TFR		(true,	false,	0,		"extended data transfer"),
STATUS_ON		(true,	false,	0,		"status on"),
STATUS_OFF		(true,	false,	0,		"status off"),
STATUS_REQ		(true,	false,	1,		"status request");

private final boolean addr;
private final boolean dim;
private final int response_len;
private final String label;
private static final Cmd[] values = values();

Cmd(boolean a, boolean d, int res, String l)
{
	addr = a;
	dim = d;
	response_len = res;
	label = l;
}

static Cmd lookup(int n)
{
	return values[n];
}

boolean need_addr()
{
	return addr;
}

boolean need_dim()
{
	return dim;
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
