package bittech.lib.protocol.commands;

import java.util.LinkedList;
import java.util.List;

import bittech.lib.protocol.Command;
import bittech.lib.protocol.common.NoDataResponse;

public class RegisterApiClientCommand extends Command<RegisterApiClientRequest, NoDataResponse> {

	public static RegisterApiClientCommand createStub() {
		return new RegisterApiClientCommand(new LinkedList<String>());
	}

	public RegisterApiClientCommand(final List<String> patterns) {
		this.request = new RegisterApiClientRequest(patterns);
	}

}
