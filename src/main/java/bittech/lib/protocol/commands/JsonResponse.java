package bittech.lib.protocol.commands;

import bittech.lib.protocol.Response;
import bittech.lib.utils.Require;
import bittech.lib.utils.json.RawJson;

public class JsonResponse implements Response {

	public RawJson body;

	public JsonResponse(RawJson body) {
		this.body = Require.notNull(body, "body");
	}

}
