package bittech.lib.protocol.commands;

import bittech.lib.protocol.Response;

public class DisconnectResponse implements Response {

	boolean success;

	public DisconnectResponse(boolean success) {
		this.success = success;
	}

}
