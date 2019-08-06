package bittech.lib.protocol;

import bittech.lib.protocol.commands.JsonCommand;
import bittech.lib.utils.Require;
import bittech.lib.utils.json.JsonBuilder;
import bittech.lib.utils.json.RawJson;

public class Message {

	public long id;
	public String name;
	public String type; // request, response, error
	public String authKey;
	public long timeout; // timeout in millisec
	public RawJson body;

	public Message(long id, Command<?, ?> command) {
		this.id = id;
		this.type = "request";
		this.timeout = command.getTimeout();
		if(command instanceof JsonCommand) {
			JsonCommand cmd = (JsonCommand)command;
			this.name = cmd.getRequest().name;
			this.body = cmd.getRequest().body;
		} else {
			this.name = command.getClass().getCanonicalName();
			this.body = new RawJson(command.getRequest());
		}
		
	}

	// Used by webNode only
	public Message(long id, String command, Request request) { 
		Require.notNull(request, "request");
		this.name = command;
		this.type = "request";
		this.timeout = 0;
		this.id = id;
		this.body = new RawJson(request);
	}
	
	// Used by webNode only
	public Message(long id, String command, Response response) { 
		Require.notNull(response, "response");
		this.name = command;
		this.type = "response";
		this.timeout = 0;
		this.id = id;
		this.body = new RawJson(response);
	}

	// Used by webNode only
	public Message(long id, String command, ErrorResponse error) {
		Require.notNull(error, "response");
		this.name = command;
		this.type = "error";
		this.timeout = 0;
		this.id = id;
		this.body = new RawJson(error);
	}

	public String toString() {
		return JsonBuilder.build().toJson(this);
	}
}