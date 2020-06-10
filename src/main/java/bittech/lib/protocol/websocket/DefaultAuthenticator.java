package bittech.lib.protocol.websocket;

import java.util.HashMap;
import java.util.Map;

import bittech.lib.protocol.websocket.AuthenticationModule.User;

public class DefaultAuthenticator implements Authenticator {
	
	private Map<String, User> users = new HashMap<String, User>();

	public DefaultAuthenticator() {
		users.put("Watcher", new User("Watcher", "6f720991fceb1250aadf2aeb55e62f69f77573875661c5ad6f173a2184ee53b2"));
		users.put("Cezary", new User("Cezary", "dd423f27dce8ae5e8300bd4cd479c62570953578dd968a7634a532ee400aec50"));
		users.put("Krystian", new User("Krystian", "dd423f27dce8ae5e8300bd4cd479c62570953578dd968a7634a532ee400aec50"));
	}
	
	
	@Override
	public User authenticate(String name, String pwdSha256) throws Exception {
		User user = users.get(name);
		if (user == null) {
			throw new Exception("No user with such name: " + name);
		}
		if (!user.pwdSha256.equals(pwdSha256)) {
			throw new Exception("Wrong password for user: " + name);
		}
		return user;
	}

}
