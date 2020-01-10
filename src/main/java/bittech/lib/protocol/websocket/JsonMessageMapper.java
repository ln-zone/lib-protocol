package bittech.lib.protocol.websocket;

import com.google.gson.Gson;

import bittech.lib.protocol.Message;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;

public class JsonMessageMapper {

	public static String toJson(Message messageWithId) throws StoredException {
		try {

			Require.notNull(messageWithId, "messageWithId");
			Gson json = JsonBuilder.build();
			String output = json.toJson(messageWithId);
			output = output.replace("\n", "").replace("\r", "");

			if (JsonBuilder.isValid(output) == false) {
				throw new StoredException("Invalid json output creatd: " + output, null);
			}

			return output;

		} catch (Exception ex) {
			throw new StoredException("Cannot convert object to Json", ex);
		}
	}

	public static Message fromJson(String json) {
		if (JsonBuilder.isValid(json) == false) {
			throw new StoredException("This is not valid json", null);
		}
		Message message = null;
		try {
			message = JsonBuilder.build().fromJson(json, Message.class);
			if (message == null) {
				throw new StoredException("Json not match protocol standard", null);
			}
		} catch (Exception ex) {
			throw new StoredException("Json not match protocol standard", ex);
		}
		return message;
	}

}
