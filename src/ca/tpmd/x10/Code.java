package ca.tpmd.x10;

enum Code
{
	M,//		13	0
	E,//		5	1
	C,//		3	2
	K,//		11	3
	O,//		15	4
	G,//		7	5
	A,//		1	6
	I,//		9	7
	N,//		14	8
	F,//		6	9
	D,//		4	A
	L,//		12	B
	P,//		16	C
	H,//		8	D
	B,//		2	E
	J;//		10	F

	private static final Code[] values = values();

	static Code lookup(int n)
	{
		return values[n];
	}

	static Code find(int n)
	{
		return Code.valueOf("" + (char)(n + 'A'));
	}
}
