package bittech.lib.protocol.commands;

import bittech.lib.protocol.Request;
import bittech.lib.utils.Require;
import bittech.lib.utils.json.RawJson;

public class JsonRequest implements Request {

	public String name;
	public RawJson body;

	public JsonRequest(String name, RawJson body) {
		this.name = Require.notNull(name, "name");
		this.body = Require.notNull(body, "body");
	}

}
