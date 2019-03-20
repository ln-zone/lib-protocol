package bittech.lib.protocol;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import bittech.lib.protocol.commands.DisconnectCommand;
import bittech.lib.protocol.commands.IntroduceCommand;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;

public class WebNode extends WebSocketServer implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketServer.class);

	private final WebDataTrensferredListener dataListener;// Daje możliwość zapisania każdego requestu i responsu

	private final ListenersManager listenersManager = new ListenersManager();

	private Exception thrownException = null;

	private Map<Long, Command<?, ?>> sentCommands = new ConcurrentHashMap<Long, Command<?, ?>>();

	private AtomicBoolean started = new AtomicBoolean(false);

	private boolean authenticated = true;

	String peerName = "WebNode client";

	public WebNode(int port) throws UnknownHostException {
		super(new InetSocketAddress(port));
		this.dataListener = null;
	}

	public WebNode(int port, WebDataTrensferredListener dataListener) throws UnknownHostException {
		super(new InetSocketAddress(port));
		this.dataListener = dataListener;
	}

	public void registerListener(Listener listener) {
		Require.notNull(listener, "listener");
		listenersManager.registerListener(listener);
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {

		LOGGER.debug(conn.getRemoteSocketAddress().getAddress().getHostAddress() + "connected");
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {

		LOGGER.debug(conn + " disconnected");
	}

	@Override
	public void onMessage(WebSocket conn, String input) {
		LOGGER.debug("WebSocket text received: " + input);
		try {

			if (dataListener != null) {
				dataListener.received(input);
			}

			Gson json = JsonBuilder.build();

			LOGGER.debug(peerName + " -> WN INPUT: " + input);
			LOGGER.debug(peerName + " -> Validating input");
			if (JsonBuilder.isValid(input) == false) {
				LOGGER.debug(peerName + " -> Input invalid! Throwing exception");
				// throw new StoredException("Invalid json received: " + input, null);
				respError(conn, 0, "Unknown", new ErrorResponse(0, "This is not valid json"));
				return;
			}
			LOGGER.debug(peerName + " -> Input is valid");

			LOGGER.debug(peerName + " -> Reading json input to message object");
			Message message = json.fromJson(input, Message.class);
			if (message == null) {
				// throw new StoredException("Null message genarated from json: " + input,
				// null);
				respError(conn, 0, "Unknown", new ErrorResponse(0, "Json not match protocol standard"));
				return;
			}

			LOGGER.debug(peerName + " -> Checkin what is input type");
			if ("response".equals(message.type)) {
				handleResponse(message);
				LOGGER.debug(peerName + " -> Out of handleResponse method");
			} else if ("error".equals(message.type)) {
				handleError(message);
			} else if ("request".equals(message.type)) {
				handleRequest(conn, message);
			} else {
				LOGGER.debug(peerName + " -> Unknown object received. Throwing exception");
				StoredException sex = new StoredException("Unknown object type received: " + message
						+ ". Only Commands and responses and errors are allowed", null);

				Message err = new Message(message.id, message.name, new ErrorResponse(sex.getId(), sex.getMessage()));
				sendObject(conn, err);

			}
			// LOGGER.info(peerName + " -> Started: " + started);
			LOGGER.debug(peerName + " -> Getting started");
			LOGGER.debug(peerName + " -> Started: " + started.get());
			LOGGER.debug(peerName + " -> Started got");
			LOGGER.debug(peerName + " -> Started: " + started.get());
			LOGGER.debug(peerName + " -> Started: " + started.get());
			boolean startedB = started.get();
			LOGGER.debug(peerName + " -> StartedB: " + startedB);

			if (startedB) {
				LOGGER.debug(peerName + " -> Started is still true. Loop should continue working");
			} else {
				LOGGER.debug(peerName + " -> Started false. Leaving message receiving loop");
				LOGGER.info(peerName + " -> Started set na false. No longer listening to commands");
				return;
			}
		} catch (Exception ex) {
			new StoredException("Cannot process webScoket message: " + input, ex);
		} finally {
			LOGGER.debug(peerName + " -> OUT OF LOOP!");
		}

	}

	private void handleResponse(Message message) {
		Require.notNull(message, "message");
		if ("response".equals(message.type) == false) {
			throw new StoredException(
					"Cannot handle response. Given message is not response type, but it is: " + message.type, null);
		}
		LOGGER.debug(peerName + " -> Input type is 'response'");
		LOGGER.debug(peerName + " -> Looking for command related with this input (id = " + message.id + ")");
		Command command = (Command) sentCommands.get(message.id);

		if (command == null) {
			LOGGER.debug(peerName + " -> No command related with this id. Throwing exception");
			throw new StoredException(peerName + " -> Cannot find command with id: " + message.id, null);
		}
		LOGGER.debug(peerName + " -> Found related command: " + command);

		LOGGER.debug(peerName + " -> Synchronizing command");
		synchronized (command) {
			LOGGER.debug(peerName + " -> Inside synchronized block");
			// LOGGER.debug(peerName + " -> Response received for command " +
			// command);
			LOGGER.debug(peerName + " -> Assigning response from json to command");
			command.response = (Response) JsonBuilder.build().fromJson(message.body.toString(),
					command.getResponseClass());
			LOGGER.debug(peerName + " -> Response added to command " + command);
			LOGGER.debug(peerName + " -> Waiting for notify");
			command.notify();
			LOGGER.debug(peerName + " -> Notified ");
		}

		LOGGER.debug(peerName + " -> Checking if this is 'disconnect' command");
		if (command instanceof DisconnectCommand) {
			LOGGER.debug(peerName + " -> Yes, we received response for disconnect commmand");
			LOGGER.debug("Peer " + peerName
					+ " replied to disconnection. Now I can disconnect. He is no longer listening for my commands");
			return;
		}
		LOGGER.debug(peerName + " -> No, this is not disconnect commmand");

	}

	private void handleError(Message message) {
		Require.notNull(message, "message");
		if ("error".equals(message.type) == false) {
			throw new StoredException("Cannot handle error. Given message is not error type but it is: " + message.type,
					null);
		}
		LOGGER.debug(peerName + " -> Input type is 'error'");
		Command command = (Command) sentCommands.get(message.id);

		LOGGER.debug(peerName + " -> Looking for command related with this input (id = " + message.id + ")");
		if (command == null) {
			LOGGER.debug(peerName + " -> No command related with this id. Throwing exception");
			throw new StoredException(peerName + " -> Cannot find command with id: " + message.id, null);
		}
		LOGGER.debug(peerName + " -> Found related command: " + command);

		LOGGER.debug(peerName + " -> Synchronizing command");
		synchronized (command) {

			LOGGER.debug(peerName + " -> Inside synchronized block");
			// LOGGER.debug(peerName + " -> Response received for command " +
			// command);
			LOGGER.debug(peerName + " -> Assigning error from json to command");

			// LOGGER.debug(peerName + " -> Error received for command " + command);
			command.error = JsonBuilder.build().fromJson(message.body.toString(), ErrorResponse.class);
			LOGGER.debug(peerName + " -> Error added to command " + command);

			LOGGER.debug(peerName + " -> Waiting for notify");
			command.notify();
			LOGGER.debug(peerName + " -> Notified ");
		}
	}

	private void handleRequest(WebSocket conn, Message message) {
		Require.notNull(message, "message");
		if ("request".equals(message.type) == false) {
			throw new StoredException(
					"Cannot handle request. Given message is not request type but it is: " + message.type, null);
		}
		LOGGER.debug(peerName + " -> Input type is 'request'");
		try {
			Method method;
			Class<?> clazzCmd;
			LOGGER.debug("--------------------------------------- 1");
			try {
				LOGGER.debug(peerName + " -> Craeting new command from stub: " + message.name);
				clazzCmd = Class.forName(message.name);
				LOGGER.debug("--------------------------------------- 2");
			} catch (Exception ex) {
				LOGGER.debug("--------------------------------------- 3");
				StoredException sex = new StoredException("Unknown command: " + message.name, ex);
				LOGGER.error("-----------------------------------" + sex.getMessage());
				respError(conn, message.id, message.name, new ErrorResponse(sex.getId(), sex.getMessage()));
				LOGGER.debug("--------------------------------------- 4");
				return;
			}
			LOGGER.debug("--------------------------------------- 5");

			try {
				method = clazzCmd.getMethod("createStub");
			} catch (Exception ex) {
				StoredException sex = new StoredException("Command " + message.name + " do not have createStub method",
						ex);
				LOGGER.error("-----------------------------------" + sex.getMessage());
				respError(conn, message.id, message.name, new ErrorResponse(sex.getId(), sex.getMessage()));
				return;
			}

			Command<Request, Response> command = (Command<Request, Response>) method.invoke(null);
			command.setTimeout(message.timeout);
			LOGGER.debug(peerName + " -> Command created from stub: " + command);

			Message response;

			LOGGER.debug(peerName + " -> Checking for authentication and if given command is IntroduceCommand ");
			if (authenticated == false && ((Command<?, ?>) command instanceof IntroduceCommand == false)) {
				LOGGER.debug(peerName
						+ " -> Not authenticated for command not Introduce. Preparing 'You are not authenticated' error response.");
				ErrorResponse err = new ErrorResponse(0, "Cannot execute command " + command.type
						+ ". You are not authenticated. Call IntroduceCommand first");

				LOGGER.debug(peerName + " -> Create new message with error to sent later on");
				response = new Message(message.id, command.getClass().getCanonicalName(), err);
			} else {
				try {
					LOGGER.debug(peerName + " -> We are authenticated");
					LOGGER.debug(peerName + " -> Reading request from json");
					Request r = (Request) JsonBuilder.build().fromJson(message.body.toString(),
							command.getRequestClass());
					command.request = r;

					long time = (new Date()).getTime();
					LOGGER.debug(peerName + " -> Createing new thread for process command");
					Thread th = new Thread(() -> {
						try {
							LOGGER.debug(peerName + " -> Call listeners for command");
							listenersManager.commandReceived(peerName, command);
							LOGGER.debug(peerName + " -> Command processed without errors");
						} catch (Exception e) {
							LOGGER.debug(peerName + " -> Command processed with error: " + e.getMessage());
							StoredException sex = new StoredException("Cannot process command", e);
							LOGGER.debug(peerName + " -> Assigning error to commmand");
							command.error = new ErrorResponse(sex);
						}
					});
					LOGGER.debug(peerName + " -> Running new thread for process command");
					th.start();
					LOGGER.debug(peerName + " -> Waiting for thread to complete");
					th.join(command.getTimeout());
					LOGGER.debug(peerName + " -> Thread 'finished'. Checking if timeout was exeeded");
					long timeLambda = (new Date()).getTime() - time;
					if (timeLambda > command.getTimeout()) {
						LOGGER.debug(peerName + " -> Timeout exeeded. Interrupting thread");
						th.interrupt();
						LOGGER.debug(peerName + " -> Thread interrupted. Throwing exception");
						throw new Exception(
								"Command execution timeout (took long than " + command.getTimeout() + " milisec");
					}
				} catch (Exception ex) {
					LOGGER.debug(peerName + " -> Exception catched for request processing: " + ex.getMessage());
					StoredException sex = new StoredException("listener.commandReceived thrown: " + ex.getMessage(),
							ex);
					LOGGER.debug(peerName + " -> Assigning given exception to command.error");
					command.error = new ErrorResponse(sex);
				}

				LOGGER.debug(peerName + " -> Checking if command contains response or error");
				if (command.response == null && command.error == null) {
					LOGGER.debug(peerName + " -> Command not contains response or error. Throwing exception");
					throw new Exception(peerName + " -> Listener didn't added response or error to command");
				}

				LOGGER.debug(peerName + " -> Checking if command contains both response and error");
				if (command.response != null && command.error != null) { // TODO:
																			// Warning
																			// only?
					LOGGER.debug(peerName + " -> Command contains both response and error");

					LOGGER.warn(peerName + " -> Listener added both response and error to command");
				}

				LOGGER.debug(peerName + " -> Preparing to send on of command's field");
				if (command.response != null) {
					LOGGER.debug(peerName + " -> Assigning command.response to message");
					response = new Message(message.id, command.getClass().getCanonicalName(), command.response);
				} else if (command.error != null) {
					LOGGER.debug(peerName + " -> Assigning command.error to message");
					response = new Message(message.id, command.getClass().getCanonicalName(), command.error);
				} else {
					LOGGER.debug(peerName + " -> Command not containing response or error");
					StoredException sex = new StoredException(
							peerName + " -> Internal error. Listener didn't add response or error to command", null);
					LOGGER.debug(peerName + " -> Assigning 'Internal error' to command.error");
					command.error = new ErrorResponse(sex.getId(), sex.getMessage());
					LOGGER.debug(peerName + " -> Assigning command.error to message");
					response = new Message(message.id, command.getClass().getCanonicalName(), command.error);
				}
				// LOGGER.debug(peerName + " -> Sending back response for command " +
				// command);
			}
			LOGGER.debug(peerName + " -> Sending reply: " + response);
			sendObject(conn, response);
			LOGGER.debug(peerName + " -> Response sent for command " + command);
		} catch (Exception ex) {
			LOGGER.debug(peerName + " ->Exception thrown. Throw it higher");
			// executionThrownException = ex;
			throw new StoredException("Handle request faild", ex); // TODO: Is this OK?
		}
	}

	private void respError(WebSocket conn, long requestId, String commandName, ErrorResponse errorResponse) {
		Message msg = new Message(requestId, commandName, errorResponse);
		sendObject(conn, msg);
	}

	@Override
	public void onMessage(WebSocket conn, ByteBuffer message) {
		LOGGER.debug("WebSocket bytes received: " + message);
		broadcast(message.array());
		LOGGER.debug(conn + ": " + message);
	}

	private synchronized void sendNativeObject(WebSocket conn, Message messageWithId) throws StoredException {
		try {
			try {

				LOGGER.debug("Sending object from to " + peerName);
				Require.notNull(messageWithId, "messageWithId");
				Gson json = JsonBuilder.build();
				String output = json.toJson(messageWithId);
				output = output.replace("\n", "").replace("\r", "");

				if (JsonBuilder.isValid(output) == false) {
					throw new StoredException("Invalid json output creatd: " + output, null);
				}
				LOGGER.info(peerName + " -> WN OUTPUT: " + output);

				if (conn != null) {
					informSent(output);
					conn.send(output);
				} else {
					informBroadcasted(output);
					broadcast(output);
				}
			} catch (Exception e) {
				new StoredException("Cannot send object to webSocket client", e);
				e.printStackTrace();
				LOGGER.error(e.toString());
			}
		} catch (Throwable th) {
			StoredException ex1 = new StoredException("Cannot sendNativeObject", th);
			// try {
			// closeSocket();
			// } catch (Throwable th2) {
			// throw new StoredException("Cannot close socket", ex1);
			// }
			throw ex1;
		}
	}

	private void informSent(String output) {
		try {
			if (dataListener != null)
				dataListener.sent(output);
		} catch (Exception ex) {
			new StoredException("Data listener.Sent failed for output '" + output + "'", ex);
		}
	}

	private void informBroadcasted(String output) {
		try {
			if (dataListener != null)
				dataListener.broadcasted(output);
		} catch (Exception ex) {
			new StoredException("Data listener.Sent failed for output '" + output + "'", ex);
		}
	}

	private synchronized void sendObject(WebSocket conn, Message response) throws StoredException {
		try {
			Require.notNull(response, "response");
			sendNativeObject(conn, response);
		} catch (Throwable th) {
			throw new StoredException("Cannot send MessageWithId object: " + response, th);
		}
	}

	// public static void main( String[] args ) throws InterruptedException ,
	// IOException {
	// WebSocketImpl.DEBUG = true;
	// int port = 8887; // 843 flash policy port
	// try {
	// port = Integer.parseInt( args[ 0 ] );
	// } catch ( Exception ex ) {
	// }
	// WebNode s = new WebNode( port );
	// s.start();
	// LOGGER.debug( "ChatServer started on port: " + s.getPort() );
	//
	// BufferedReader sysin = new BufferedReader( new InputStreamReader( System.in )
	// );
	// while ( true ) {
	// String in = sysin.readLine();
	// s.broadcast( in );
	// if( in.equals( "exit" ) ) {
	// s.stop(1000);
	// break;
	// }
	// }
	// }

	@Override
	public void onError(WebSocket conn, Exception ex) {
		ex.printStackTrace();
		if (conn != null) {
			// some errors like port binding failed may not be assignable to a specific
			// websocket
		}
	}

	@Override
	public void onStart() {
		LOGGER.debug("Server started!");
	}

	@Override
	public void close() throws Exception {
		stop();
	}

	public void execute(Command<?, ?> command) throws StoredException {
		try {
			if ((command instanceof IntroduceCommand == false) && (authenticated == false)) {
				throw new StoredException("Cannot execute command. Peer not authenticated", null);
			}

			Require.notNull(command, "command");
			if (command.getError() != null) {
				LOGGER.warn("Command to execute contains error");
				command.error = null;
			}
			if (command.getResponse() != null) {
				LOGGER.warn("Command to execute contains response");
				command.response = null;
			}
			synchronized (command) {
				long timeout = command.getTimeout();
				sendObject(null, command);
				LOGGER.debug("Waiting for response to command: " + command);

				// TMP: Not wait (it is broadcasted
				// long time = (new Date()).getTime();
				// command.wait(timeout);
				// long delta = (new Date()).getTime() - time;
				// if (delta >= timeout) {
				// throw new Exception("Timeout occured waiting for reply from " + peerName);
				// }
				// if (command.response == null && command.error == null) {
				// throw new Exception(peerName + " -> Command was unlocked without result: " +
				// command);
				// }
			}
			LOGGER.debug("Command received: " + command);
		} catch (Throwable th) {
			throw new StoredException("Cannot execute command: " + command, th);
		}

	}

	private synchronized void sendObject(WebSocket conn, Command<?, ?> command) throws StoredException {
		try {
			Require.notNull(command, "command");
			Message o = new Message(0, command);
			o.id = addCommand(command);
			sendNativeObject(conn, o);
		} catch (Throwable th) {
			throw new StoredException("Cannot send command object: " + command, th);
		}
	}

	private long addCommand(Command<?, ?> command) throws StoredException {
		try {
			Require.notNull(command, "command");
			long id = (long) (Math.random() * Long.MAX_VALUE);
			LOGGER.debug("Adding command with id " + id + " to map. Command: " + command);
			synchronized (sentCommands) {
				sentCommands.put(id, command);
			}
			return id;
		} catch (Throwable th) {
			throw new StoredException("Cannot add command to sentCommands list. Command: " + command, th);
		}
	}

}
