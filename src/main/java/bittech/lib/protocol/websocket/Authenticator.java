package bittech.lib.protocol.websocket;

import bittech.lib.protocol.websocket.AuthenticationModule.User;

public interface Authenticator {
	
	public User authenticate(final String name, final String pwdSha256) throws Exception;

}
