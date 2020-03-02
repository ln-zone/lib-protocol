package bittech.lib.protocol.websocket;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.protocol.Command;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.ListenersManager;
import bittech.lib.protocol.Message;
import bittech.lib.protocol.Request;
import bittech.lib.protocol.Response;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;

public class JsonCommandExec {

	private static final Logger LOGGER = LoggerFactory.getLogger(JsonCommandExec.class);

	private final ListenersManager listenersManager;
	private Map<Long, Command<?, ?>> sentCommands = new ConcurrentHashMap<Long, Command<?, ?>>();

	public JsonCommandExec(final ListenersManager listenersManager) {
		this.listenersManager = Require.notNull(listenersManager, "listenersManager");
	}

//	public String prepareToSend(Command<?, ?> command) {
//		long id = (long)(Math.random()*Long.MAX_VALUE);
//		Message message = new Message(id, command.getClass().getCanonicalName(), command.request);
//		sentCommands.put(id, command);
//		return JsonBuilder.build().toJson(message);
//	}

	private String respError(long requestId, String commandName, ErrorResponse errorResponse) {
		return JsonMessageMapper.toJson(new Message(requestId, commandName, errorResponse));
	}

	public void waitForResponse(Command<?, ?> command) {
		try {
			synchronized (command) {
				command.wait();
			}
		} catch (Exception ex) {
			throw new StoredException("Wait for response failed", ex);
		}
	}

	@SuppressWarnings("unchecked")
	private void handleResponse(Message message) {
		Require.notNull(message, "message");
		if ("response".equals(message.type) == false) {
			throw new StoredException(
					"Cannot handle response. Given message is not response type, but it is: " + message.type, null);
		}

		@SuppressWarnings("rawtypes")
		Command command = (Command) sentCommands.get(message.id);

		if (command == null) {
			throw new StoredException("Cannot find command with id: " + message.id, null);
		}

		synchronized (command) {
			command.response = (Response) JsonBuilder.build().fromJson(message.body.toString(),
					command.getResponseClass());

			command.notify();
		}
	}

	@SuppressWarnings("unchecked")
	private void handleError(Message message) {
		Require.notNull(message, "message");
		if ("error".equals(message.type) == false) {
			throw new StoredException("Cannot handle error. Given message is not error type but it is: " + message.type,
					null);
		}
		@SuppressWarnings("rawtypes")
		Command command = (Command) sentCommands.get(message.id);

		if (command == null) {
			throw new StoredException("Cannot find command with id: " + message.id, null);
		}

		synchronized (command) {
			command.error = JsonBuilder.build().fromJson(message.body.toString(), ErrorResponse.class);
			command.notify();
		}
	}

//	public static int idStatic = 0;

	public String prepareToSend(String authKey, Command<?, ?> command) {
//		idStatic++;
//		long id = idStatic;
		long id = (long) (Math.random() * Long.MAX_VALUE);
		Message message = new Message(id, command.getClass().getCanonicalName(), command.request);
		message.authKey = authKey;
		sentCommands.put(id, command);
		return JsonMessageMapper.toJson(message);
	}

	public static String commandRequestToJson(Command<?, ?> command) {
		long id = (long) (Math.random() * Long.MAX_VALUE);
		Message message = new Message(id, command.getClass().getCanonicalName(), command.request);
		return JsonMessageMapper.toJson(message);
	}

	private String handleRequest(String channelId, Message message) {
		Require.notNull(message, "message");
		if ("request".equals(message.type) == false) {
			throw new StoredException(
					"Cannot handle request. Given message is not request type but it is: " + message.type, null);
		}

		try {
			Method method;
			Class<?> clazzCmd;

			try {
				clazzCmd = Class.forName(message.name);
			} catch (Exception ex) {
				StoredException sex = new StoredException("Unknown command: " + message.name, ex);
				return respError(message.id, message.name, new ErrorResponse(sex.getId(), sex.getMessage()));

			}

			try {
				method = clazzCmd.getMethod("createStub");
			} catch (Exception ex) {
				StoredException sex = new StoredException("Command " + message.name + " do not have createStub method",
						ex);

				return respError(message.id, message.name, new ErrorResponse(sex.getId(), sex.getMessage()));
			}

			@SuppressWarnings("unchecked")
			Command<Request, Response> command = (Command<Request, Response>) method.invoke(null);
			command.setTimeout(message.timeout);
			command.setAuthKey(message.authKey);

			Message response;

			try {
				Request r = (Request) JsonBuilder.build().fromJson(message.body.toString(), command.getRequestClass());
				command.request = r;

				long time = (new Date()).getTime();

				Thread th = new Thread(() -> {
					try {
						listenersManager.commandReceived(channelId, command);

					} catch (Exception e) {
						StoredException sex = new StoredException(
								"Cannot process command '" + message == null ? null : message.name + "'", e);
						command.response = null;
						command.error = new ErrorResponse(sex);
					}
				});

				th.start();

				if (command.getTimeout() == 0) {
					th.join(command.getTimeout());
				} else {
					th.join(command.getTimeout());

					long timeLambda = (new Date()).getTime() - time;
					if (timeLambda > command.getTimeout()) {

						th.interrupt();

						throw new Exception(
								"Command execution timeout (took long than " + command.getTimeout() + " milisec");
					}
				}
			} catch (Exception ex) {
				StoredException sex = new StoredException("listener.commandReceived thrown: " + ex.getMessage(), ex);
				command.error = new ErrorResponse(sex);
			}

			if (command.response == null && command.error == null) {

				command.error = new ErrorResponse(new StoredException("Cannot process command",
						new Exception("Listener didn't added response or error to command")));
			}

			if (command.response != null && command.error != null) { // TODO:
																		// Warning
																		// only?
				LOGGER.warn(
						"Listener added both response and error to command: " + JsonBuilder.build().toJson(command));
			}

			if (command.response != null) {
				response = new Message(message.id, command.getClass().getCanonicalName(), command.response);
			} else if (command.error != null) {
				response = new Message(message.id, command.getClass().getCanonicalName(), command.error);
			} else {

				StoredException sex = new StoredException(
						"Internal error. Listener didn't add response or error to command", null);
				command.error = new ErrorResponse(sex.getId(), sex.getMessage());

				response = new Message(message.id, command.getClass().getCanonicalName(), command.error);
			}

//			}
			return JsonMessageMapper.toJson(response);
		} catch (Exception ex) {
			throw new StoredException("Handle request faild", ex); // TODO: Is this OK?
		}
	}

	public String onReceived(String channelId, String input) {

		try {

			Message message;
			try {
				message = JsonMessageMapper.fromJson(input);
			} catch (Exception ex) {
				return respError(0, "Unknown", new ErrorResponse(ex));
			}

			if ("response".equals(message.type)) {
				handleResponse(message);
				return null;
			} else if ("error".equals(message.type)) {
				handleError(message);
				return null;
			} else if ("request".equals(message.type)) {
				return handleRequest(channelId, message);
			} else {
				StoredException sex = new StoredException("Unknown object type received: " + message
						+ ". Only Commands and responses and errors are allowed", null);

				Message err = new Message(message.id, message.name, new ErrorResponse(sex.getId(), sex.getMessage()));
				return JsonMessageMapper.toJson(err);

			}

		} catch (Exception ex) {
			throw new StoredException("Cannot process message: " + input, ex);
		}

	}

}
