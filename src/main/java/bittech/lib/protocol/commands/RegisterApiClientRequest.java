package bittech.lib.protocol.commands;

import java.util.List;

import bittech.lib.protocol.Request;
import bittech.lib.utils.Require;

public class RegisterApiClientRequest implements Request {

	public final List<String> patterns;

	public RegisterApiClientRequest(final List<String> patterns) {
		this.patterns = Require.notNull(patterns, "patterns");
	}

}
