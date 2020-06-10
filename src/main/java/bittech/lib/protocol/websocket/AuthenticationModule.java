package bittech.lib.protocol.websocket;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import bittech.lib.protocol.Command;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.commands.LoginCommand;
import bittech.lib.protocol.commands.LoginResponse;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.logs.Log;

public class AuthenticationModule implements Listener {

	public static class User {
		String name;
		String pwdSha256;

		public User(final String name, final String pwdSha256) {
			this.name = Require.notEmpty(name, "name");
			this.pwdSha256 = Require.notEmpty(pwdSha256, "pwdSha256");
		}
	}

	public static class AuthToken {
		String key;
		long lastUsage;
		User user;
	}

	private Authenticator authenticator;

	private Map<String, AuthToken> authTokens = new HashMap<String, AuthToken>();

	public AuthenticationModule() {
		this.authenticator = new DefaultAuthenticator();
	}
		
	public synchronized void setAuthenticator(final Authenticator authenticator) {
		this.authenticator = Require.notNull(authenticator, "authenticator");
	}

	public synchronized String authenticate(final String name, final String pwdSha256) {
		try {

			User user = authenticator.authenticate(name, pwdSha256);

			AuthToken authToken = new AuthToken();
			authToken.lastUsage = new Date().getTime();
			authToken.user = user;
			authToken.key = DigestUtils.sha1Hex(user.name + user.pwdSha256 + authToken.lastUsage);
			authTokens.put(authToken.key, authToken);
			Log.build().param("Nazwa użytkownika", user.name).event("User " + user.name + " logged in successfully");
			return authToken.key;
		} catch (Exception ex) {
			throw new StoredException("Cannot authenticate user: " + name, ex);
		}
	}

	@Override
	public Class<?>[] getListeningCommands() {
		return new Class<?>[] { LoginCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		if (command instanceof LoginCommand) {
			LoginCommand cmd = (LoginCommand) command;
			cmd.response = new LoginResponse(authenticate(cmd.getRequest().name, cmd.getRequest().pwdSha256));
		} else {
			throw new StoredException("Unsupported command type: " + command.type, null);
		}
	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
	}

	public synchronized void checkPermission(String authKey) {
		if (authTokens.get(authKey) == null) {
			throw new StoredException("You are not authenticated to make this operation",
					new Exception("Auth kay not valid: " + authKey));
		}
	}

}
