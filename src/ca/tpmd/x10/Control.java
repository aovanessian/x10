package ca.tpmd.x10;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.NoSuchElementException;

public class Control implements Runnable
{

private static Serial _serial;
private static Control _control = null;

public static Control create(Serial s)
{
	if (_control == null)
		_control = new Control(s);
	return _control;
}

private Control(Serial s)
{
	_serial = s;
}

private Command parse(String s)
{
	ArrayList<String> tokens = tokenize(s);
	if (tokens.size() == 0) {
		X10.warn("Empty command");
		return null;
	}
	Cmd command = command(tokens.get(0));
	if (command == null) {
		X10.warn("Not a valid command: " + tokens.get(0));
		return null;
	}
	if (command == Cmd.EXIT)
		return new Command(command, null);
	if (tokens.size() < 2) {
		X10.warn("Not enough parameters: " + s);
		return null;
	}
	int dim = 0;
	int start = 1;
	Code house;
	if (command.need_dim()) {
		if (tokens.size() < 4) {
			X10.warn("Not enough parameters for " + command + ": " + s);
			return null;
		}
		dim = number(tokens.get(start));
		if (dim == -1)
			return null;
		if (dim < 0 || dim > 22) {
			X10.warn(command + " level outside of allowed range [0..22]: " + dim);
			return null;
		}
		start++;
	}
	house = house(tokens.get(start++).toUpperCase());
	if (house == null)
		return null;
	if (!command.need_addr())
		return new Command(command, house);
	if (tokens.size() == start) {
		X10.warn("Need at least one unit for " + command);
		return null;
	}
	int[] units = new int[tokens.size() - start];
	int n, k = 0;
	for (int i = start; i < tokens.size(); i++) {
		n = number(tokens.get(i));
		if (n == -1)
			return null;
		if (n < 1 || n > 16) {
			X10.warn("Unit id outside of allowed range [1..16]: " + n);
			return null;
		}
		units[k++] = n;
	}
	return new Command(command, house, units, dim);
}

private ArrayList<String> tokenize(String s)
{
	ArrayList<String> result = new ArrayList<String>();
	char[] tmp = s.toCharArray();
	int i, b = 0;
	for (i = 0; i < tmp.length; i++) {
		switch (tmp[i]) {
		case ' ':
		case '\t':
			if (i > b)
				result.add(s.substring(b, i));
			b = i + 1;
			break;
		}
	}
	if (b < tmp.length)
		result.add(s.substring(b, tmp.length));
	for (i = 0; i < result.size(); i++)
		X10.debug("Command token: '" + result.get(i) + "'");
	return result;
}

private int number(String s)
{
	int n;
	try {
		n = Integer.parseInt(s);
	} catch (NumberFormatException x) {
		n = -1;
		X10.warn("Not a number: " + s);
	}
	return n;
}

private Code house(String s)
{
	if (s.length() != 1) {
		X10.warn("Invalid house code '" + s + "'");
		return null;
	}
	char c = s.charAt(0);
	if (c < 'A' || c > 'P') {
		X10.warn("Invalid house code '" + c + "'");
		return null;
	}
	return Code.valueOf(s);
}

private Cmd command(String s)
{
	switch (s.toLowerCase()) {
	case "ao":
	case "aoff":
	case "alloff":
	case "all-off":
	case "all_off":
		return Cmd.ALL_OFF;
	case "lon":
	case "lightson":
	case "lights-on":
	case "lights_on":
	case "alllightson":
	case "all-lights-on":
	case "all_lights_on":
		return Cmd.LIGHTS_ON;
	case "on":
		return Cmd.ON;
	case "off":
		return Cmd.OFF;
	case "dim":
		return Cmd.DIM;
	case "br":
	case "bright":
	case "brighten":
		return Cmd.BRIGHT;
	case "loff":
	case "lightsoff":
	case "lights-off":
	case "lights_off":
	case "alllightsoff":
	case "all-lights-off":
	case "all_lights_off":
		return Cmd.LIGHTS_OFF;
	case "st":
	case "status":
	case "status-request":
	case "status_request":
		return Cmd.STATUS_REQ;
	case "sys":
	case "system":
	case "state":
		return Cmd.SYSTEM_STATE;
	case "exit":
	case "quit":
		return Cmd.EXIT;
	}
	return null;
}

public void run()
{
	Scanner in = new Scanner(System.in);
	Command c;
	String s;
	for (;;) {
		try {
			s = in.nextLine();
		} catch (NoSuchElementException n) {
			continue;
		} catch (IllegalStateException x) {
			break;
		}
		c = parse(s);
		if (c != null) {
			_serial.addCommand(c);
			if (c.exit())
				break;
		}
	}
}

}
