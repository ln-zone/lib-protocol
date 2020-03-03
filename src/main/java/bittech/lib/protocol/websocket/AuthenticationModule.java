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

	private Map<String, AuthToken> authTokens = new HashMap<String, AuthToken>();

	private Map<String, User> users = new HashMap<String, User>();

	public AuthenticationModule() {
		users.put("Watcher", new User("Watcher", "6f720991fceb1250aadf2aeb55e62f69f77573875661c5ad6f173a2184ee53b2"));
		users.put("Cezary", new User("Cezary", "dd423f27dce8ae5e8300bd4cd479c62570953578dd968a7634a532ee400aec50"));
		users.put("Krystian", new User("Krystian", "dd423f27dce8ae5e8300bd4cd479c62570953578dd968a7634a532ee400aec50"));
	}

	public synchronized String authenticate(final String name, final String pwdSha256) {
		try {
			User user = users.get(name);
			if (user == null) {
				throw new Exception("No user with such name: " + name);
			}
			if (!user.pwdSha256.equals(pwdSha256)) {
				throw new Exception("Wrong password for user: " + name);
			}

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
