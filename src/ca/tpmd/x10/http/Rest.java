package ca.tpmd.x10.http;

import ca.tpmd.x10.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public final class Rest extends HttpServlet
{

private String message;
private PipedOutputStream _out = null;

public void init() throws ServletException
{
	message = "Hello World";
	String port = "/dev/ttyUSB0";
	PipedInputStream in = new PipedInputStream();
	try {
		_out = new PipedOutputStream(in);
	} catch (IOException x) {}
	Serial comm = Serial.create(port);
	X10.info("X10 control");
	if (!comm.test()) {
		X10.err("Interface at " + port + " does not respond, exiting...");
		System.exit(1);
	}
	X10.info("Interface at " + port + " ready.\n");
	Thread t = new Thread(comm);
	t.start();
	Control ctrl = Control.create(comm, in);
	new Thread(ctrl).start();
}

public void doGet(HttpServletRequest req, HttpServletResponse res)
throws ServletException, IOException
{
	res.setContentType("text/html");
	//send("ao o");
	//send("st o 1");
	PrintWriter out = res.getWriter();
	out.println(req.getParameter("c"));
	out.println("<h1>" + message + "</h1>");
}

public void destroy()
{
	send("exit");
	try {
		Thread.sleep(3000);
	} catch (InterruptedException ex) {}
}

private void send(String s)
{
	byte[] b = s.getBytes();
	try {
		_out.write(b, 0, b.length);
		_out.write('\n');
		//_out.flush();
	} catch (IOException x) {}
}

}
