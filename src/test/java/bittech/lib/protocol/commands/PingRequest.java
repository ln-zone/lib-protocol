package bittech.lib.protocol.commands;

import bittech.lib.protocol.Request;

public class PingRequest implements Request {

	public String message;

	public PingRequest() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public String toString() {
		return message;
	}

}
