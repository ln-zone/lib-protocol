package bittech.lib.protocol.commands;

import bittech.lib.protocol.Response;

public class PingResponse implements Response {

	public String message;

	public PingResponse(String message) {
		this.message = message;
		// TODO Auto-generated constructor stub
	}

}
