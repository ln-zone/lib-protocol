package bittech.lib.protocol.commands;

import bittech.lib.protocol.Command;

public class LoginCommand extends Command<LoginRequest, LoginResponse> {

	public static LoginCommand createStub() {
		return new LoginCommand("", "");
	}

	public LoginCommand(final String name, final String pwdSha256) {
		this.request = new LoginRequest(name, pwdSha256);
	}

}
