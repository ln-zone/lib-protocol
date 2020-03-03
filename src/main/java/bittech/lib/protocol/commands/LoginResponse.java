package bittech.lib.protocol.commands;

import bittech.lib.protocol.Response;
import bittech.lib.utils.Require;

public class LoginResponse implements Response {

	public String authKey;

	public LoginResponse(final String authKey) {
		this.authKey = Require.notNull(authKey, "authKey");
	}

}
