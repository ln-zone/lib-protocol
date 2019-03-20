package bittech.lib.protocol.commands;

import bittech.lib.protocol.Command;

public class PingCommand extends Command<PingRequest, PingResponse> {

	public static PingCommand createStub() {
		return new PingCommand("ping");
	}

	public PingCommand(String message) {
		this.request = new PingRequest();
		this.request.message = message;
	}

}
