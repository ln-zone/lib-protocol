package bittech.lib.protocol.commands;

import bittech.lib.protocol.Request;
import bittech.lib.utils.Require;

public class LoginRequest implements Request {

	public String name;
	public String pwdSha256;

	public LoginRequest(final String name, final String pwdSha256) {
		this.name = Require.notNull(name, "name");
		this.pwdSha256 = Require.notNull(pwdSha256, "pwdSha256");
	}

}
