package ca.tpmd.x10.eeprom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.TreeSet;
import java.util.Locale;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import ca.tpmd.x10.Cmd;
import ca.tpmd.x10.Command;
import ca.tpmd.x10.Control;
import ca.tpmd.x10.X10;

public final class Schedule
{

private final byte[] _eeprom = new byte[1024];
private final String _conf;
private final ArrayList<Trigger> _triggers;
private final ArrayList<Timer> _timers;
private final ArrayList<Macro> _macros;
private final HashMap<String, Integer> _n2o;
private final HashMap<Integer, String> _o2n;

private static final String _image = "eeprom.bin";
private static final String _xref = "xref.txt";

private static Schedule _schedule;

private Schedule(String filename)
{
	_conf = filename;
	_triggers = new ArrayList<Trigger>();
	_timers = new ArrayList<Timer>();
	_macros = new ArrayList<Macro>();
	_n2o = new HashMap<String, Integer>();
	_o2n = new HashMap<Integer, String>();
	_n2o.put("null", -1); // reserve 'null' macro name
}

static String next(ArrayList<String> tokens)
{
	if (tokens.size() != 0)
		return tokens.remove(0);
	throw new IllegalArgumentException("not enough parameters");
}

static int house(String s)
{
        if (s.length() != 1)
                throw new IllegalArgumentException("invalid house code '" + s + "'");
        char c = s.charAt(0);
        if (c >= 'a')
                c -= 32;
        if (c < 'A' || c > 'P')
                throw new IllegalArgumentException("invalid house code '" + s + "'");
        return c;
}

static int number(String s, int min, int max)
{
        int n;
        try {   
                n = Integer.parseInt(s);
        } catch (NumberFormatException x) {
                throw new IllegalArgumentException("expecting number from " + min + " to " + max + ", got '" + s + "'");
        }
        if (n >= min && n <= max)
                return n;
        throw new IllegalArgumentException("number outside of allowed range [" + min + ".." + max + "]: " + n);
}

public static Command parse(ArrayList<String> tokens)
{
	if (tokens == null || tokens.size() == 0) {
		X10.err("Bad schedule command");
		return null;
	}
	byte[] tmp;
	String token = next(tokens);
	X10.debug("element: " + token);
	switch (token) {
	case "parse":
	case "p":
		try {
			_schedule = new Schedule(next(tokens));
			_schedule.parse();
			_schedule.adjust();
		} catch (IOException | IllegalArgumentException x) {
			X10.err("schedule parse: " + x.getMessage());
			_schedule = null;
		}
		break;
	case "read":
	case "r":
		// read existing image
		break;
	case "write":
	case "w":
		if (_schedule == null) {
			X10.err("schedule empty - 'read' or 'parse' first");
			break;
		}
		return _schedule.image();
	}
	X10.debug("schedule command finished");
	return null;
}

private final void adjust()
{
	if (_macros.size() == 0)
		throw new IllegalArgumentException("no macros defined");
	if (_timers.size() == 0 && _triggers.size() == 0)
		throw new IllegalArgumentException("neither triggers nor timers defined");
	X10.info("macros:\t\t" + _macros.size() + "\n\ttriggers:\t" + _triggers.size() + "\n\ttimers:\t\t" + _timers.size());
	if (!_macros.get(_macros.size() - 1).name().equals("null")) {
		_n2o.remove("null");
		parse_line("macro null 0", -1); // add 'null' macro
		_n2o.put("null", 0);
	}
	HashMap<String, Integer> names = new HashMap<String, Integer>();
	Macro ma;
	Timer ti;
	Trigger tr;
	ArrayList<String> warn = new ArrayList<String>();
	// remove triggers referencing undefined macros
	int i = _triggers.size();
	while (i-- > 0) {
		tr = _triggers.get(i);
		String name = tr.macro();
		if (_n2o.get(name) == null) {
			warn.add("Line " + tr.line() + ": trigger macro '" + tr.macro() + "' not defined, skipping");
			_triggers.remove(i);
			continue;
		}
		names.put(name, -1);
	}
	// remove timers referencing undefined macros
	i = _timers.size();
	while (i-- > 0) {
		ti = _timers.get(i);
		if (_n2o.get(ti.macro_start()) == null) {
			warn.add("Line " + ti.line() + ": timer start macro '" + ti.macro_start() + "' not defined, skipping");
			_timers.remove(i);
			continue;
		}
		if (_n2o.get(ti.macro_end()) == null) {
			warn.add("Line " + ti.line() + ": timer end macro '" + ti.macro_end() + "' not defined, skipping");
			_timers.remove(i);
			continue;
		}
		names.put(ti.macro_start(), -1);
		names.put(ti.macro_end(), -1);
	}
	// remove unused macros
	boolean prev = false;
	for (i = 0; i < _macros.size(); i++) {
		ma = _macros.get(i);
		if (names.get(ma.name()) != null) { // referenced
			if (!prev && ma.chained()) { // part of chain but parent is unreferenced (or it's the first macro)
				if (i > 0) {
					ma.unchain(); // unchaining it so that it does not run after previously-valid macro
							// will not unchain the first one - saves a whole byte!
					X10.verbose("unchaining " + ma);
				}
			}
			prev = true;
			continue;
		}
		if (ma.chained() && prev) // unreferenced but part of chain
			continue;
		// not chained or part of unreachable chain, delete
		warn.add("Line " + ma.line() + ": macro '" + ma.name() + "' is neither chained nor referenced, skipping");
		_macros.remove(i--);
		prev = false;
	}
	Collections.sort(warn); // sort warnings by line number
	for (i = 0; i < warn.size(); i++)
		X10.warn(warn.get(i));

	// TODO
	//	remove duplicate triggers (same trigger, same macro)
	//	remove duplicate timers
	//
	//	check for duplicated triggers (same trigger, different macros) - are these allowed?
	//	check for timers set to the same day/date/time - are these allowed?
	
	// verify we have not exceeded 1024 bytes
	int size_tr = _triggers.size() > 0 ? _triggers.size() * 3 + 2 : 0;
	int size_ti = _timers.size() * 9 + 1;
	int size_ma = 0;
	i = _macros.size();
	while (i-- > 0)
		size_ma += _macros.get(i).size();
	int size = size_ma + size_tr + size_ti + 2;
	X10.debug("macros: " + size_ma + ", triggers: " + size_tr + ", timers: " + size_ti);
	if (size > 1024)
		throw new IllegalArgumentException("schedule is too large (" + size + " bytes)");
	X10.info("schedule size: " + size + " bytes (" + (1024 - size) + " free)");
}

private final void parse() throws IOException
{
	File file = new File(_conf);
	FileReader fileReader = new FileReader(file);
	BufferedReader bufferedReader = new BufferedReader(fileReader);
	String line;
	int n = 0;
	while ((line = bufferedReader.readLine()) != null)
		parse_line(line, ++n);
	X10.verbose("parsed " + n + " lines");
	fileReader.close();
}

private static final void read_image(byte[] b, HashMap<Integer, String> o2n)
{
	if (b.length != 1024)
		throw new IllegalArgumentException("Expecting 1024 bytes image, got " + b.length + "bytes");
	byte[] tmp = new byte[3];
	int n = (((b[0] & 0xff) << 8) | (b[1] & 0xff));
	int lowest = 1022;
	Trigger tr;
	do {
		if ((b[n] & 0xff) == 0xff && (b[n + 1] & 0xff) == 0xff)
			break;
		System.arraycopy(b, n, tmp, 0, tmp.length);
		tr = new Trigger(tmp, o2n);
		n += tmp.length;
		if (lowest > tr.pointer())
			lowest = tr.pointer();	
		X10.debug(X10.hex(tmp, tmp.length));
		X10.verbose(tr.toString());
	} while (true);
	tmp = new byte[9];
	n = 2;
	Timer ti;
	do {
		if ((b[n] & 0xff) == 0xff)
			break;
		System.arraycopy(b, n, tmp, 0, tmp.length);
		ti = new Timer(tmp, o2n);
		n += tmp.length;
		if (lowest > ti.pointer())
			lowest = ti.pointer();	
		X10.debug(X10.hex(tmp, tmp.length));
		X10.verbose(ti.toString());
		
	} while (true);
	n = lowest;
	tmp = new byte[1024 - n];
	Macro m;
	String name;
	do {
		System.arraycopy(b, n, tmp, 0, 1024 - n);
		name = o2n.get(n);
		boolean offset = false;
		if (name == null) {
			name = o2n.get(++n);
			offset = true;
			n--;
		}
		m = new Macro(tmp, name, offset);
		n += m.size();
		if (name.equals("null"))
			continue;
		X10.debug(X10.hex(tmp, m.size()));
		X10.verbose(m.toString());
	} while (n < 1024);
}

private final Command image()
{
	int p = _eeprom.length;
	byte[] tmp;
	int e = _macros.size();
	Macro m;
	while (e-- > 0) {
		m = _macros.get(e);
		tmp = m.serialize();
		p -= tmp.length;
		System.arraycopy(tmp, 0, _eeprom, p, tmp.length);
		_n2o.put(m.name(), p + m.offset());
		_o2n.put(p + m.offset(), m.name());
	}
	_eeprom[--p] = (byte)0xff; // triggers end marker
	_eeprom[--p] = (byte)0xff;
	e = _triggers.size();
	Trigger tr;
	while (e-- > 0) {
		tr = _triggers.get(e);
		tr.pointer(_n2o.get(tr.macro()));
		tmp = tr.serialize();
		p -= tmp.length;
		System.arraycopy(tmp, 0, _eeprom, p, tmp.length);
	}
	_eeprom[0] = (byte)(p >>> 8);
	_eeprom[1] = (byte)(p & 0xff);
	int n = p;
	p = 2;
	Timer ti;
	e = 0;
	while (e < _timers.size()) {
		ti = _timers.get(e);
		ti.pointers(_n2o.get(ti.macro_start()), _n2o.get(ti.macro_end()));
		tmp = ti.serialize();
		System.arraycopy(tmp, 0, _eeprom, p, tmp.length);
		e++;
		p += tmp.length;
	}
	_eeprom[p++] = (byte)0xff; // timers end marker
	X10.debug(X10.hex(_eeprom));
	n -= p;
	X10.info("free eeprom space: " + n + " bytes, enough for " + (n / 9) + " more timers");
	X10.info(_o2n.toString());
	StringBuilder s = new StringBuilder();
	TreeSet<Integer> o = new TreeSet<Integer>(_o2n.keySet());
	for (Integer z : o) {
		s.append("\n\t");
		s.append(z);
		s.append("\t");
		s.append(_o2n.get(z));
	}
	X10.info(s.toString());
	Schedule.read_image(_eeprom, _o2n);
	try {
		FileOutputStream eeprom = new FileOutputStream(_image);
		eeprom.write(_eeprom);
		eeprom.close();
	} catch (IOException x) {
		X10.err("Error writing image/xref files: " + x.getMessage());
		return null;
	}
	Command c = new Command(Cmd.EEPROM_WRITE);
	c.setData(_eeprom);
	return c;
}

private final void parse_line(String line, int n)
{
	ArrayList<String> tokens = Control.tokenize(line);
	if (tokens.size() == 0) {
		X10.debug("line " + n + ": empty line");
		return;
	}
	if (tokens.get(0).charAt(0) == '#') {
		X10.debug("line " + n + ": comment: " + line);
		return;
	}
	byte[] tmp;
	switch (next(tokens).toLowerCase(Locale.US)) {
	case "timer":
		Timer ti = Timer.parse(tokens, n);
		if (ti == null)
			break;
		_timers.add(ti);
		X10.verbose(ti.toString());
		break;
	case "trigger":
		Trigger tr = Trigger.parse(tokens, n);
		if (tr == null)
			break;
		_triggers.add(tr);
		X10.verbose(tr.toString());
		break;
	case "macro":
		Macro ma = Macro.parse(tokens, _n2o, n);
		if (ma == null)
			break;
		_macros.add(ma);
		_n2o.put(ma.name(), -1);
		X10.verbose(ma.toString());
		break;
	default:
		X10.warn("line " + n + ": unknown schedule command: " + line);
	}
}

}
