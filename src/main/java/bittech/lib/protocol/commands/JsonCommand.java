package bittech.lib.protocol.commands;

import bittech.lib.protocol.Command;
import bittech.lib.utils.json.RawJson;

public class JsonCommand extends Command<JsonRequest, JsonResponse> {

	public static JsonCommand createStub() {
		return new JsonCommand("", new RawJson(""));
	}

	public JsonCommand(String name, RawJson body) {
		request = new JsonRequest(name, body);
	}

}
