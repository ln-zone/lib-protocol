package bittech.lib.protocol.commands;

import bittech.lib.protocol.Command;
import bittech.lib.protocol.common.NoDataRequest;

public class DisconnectCommand extends Command<NoDataRequest, DisconnectResponse> {

	public static DisconnectCommand createStub() {
		return new DisconnectCommand();
	}

	public DisconnectCommand() {
		request = new NoDataRequest();
		timeout = 15000;
	}

}
