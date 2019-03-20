package bittech.lib.protocol.connections;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import bittech.lib.protocol.Command;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.ListenersManager;
import bittech.lib.protocol.Message;
import bittech.lib.protocol.Request;
import bittech.lib.protocol.Response;
import bittech.lib.protocol.commands.DisconnectCommand;
import bittech.lib.protocol.commands.IntroduceCommand;
import bittech.lib.protocol.commands.JsonCommand;
import bittech.lib.protocol.commands.JsonResponse;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;

public class SocketConnection implements AutoCloseable, IConnection {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocketConnection.class);

	private final ListeningThread listeningThread = new ListeningThread();

	private final ListenersManager listener;

	private Exception thrownException = null;

	private Socket socket = null;
	// private ObjectOutputStream oos;
	private PrintWriter out;

	private Map<Long, Command<?, ?>> sentCommands = new ConcurrentHashMap<Long, Command<?, ?>>();

	private AtomicBoolean started = new AtomicBoolean(false);

	private String peerName;

	private boolean authenticated = false;

	private ExecutorService threadPool = Executors.newFixedThreadPool(10);

	public SocketConnection(final ListenersManager listener, final String peerName) throws StoredException {
		try {
			this.listener = Require.notNull(listener, "listener");
			this.peerName = Require.notNull(peerName, "peerName");
		} catch (Throwable th) {
			throw new StoredException("Cannot create connection", th);
		}
	}

	/* (non-Javadoc)
	 * @see lnzone.lib.p2p.IConnection#setStarted(boolean)
	 */
	@Override
	public void setStarted(boolean started) {
		this.started.set(started);
	}

	public void start(Socket socket) throws StoredException {
		try {
			LOGGER.debug("Createing Connection with ip " + socket.getInetAddress().getHostAddress() + " on port "
					+ socket.getPort());
			this.socket = Require.notNull(socket, "socket");
			listeningThread.start();
			synchronized (listeningThread) {
				LOGGER.debug("Waiting for listening thread");
				listeningThread.wait(10000);
			}
		} catch (Throwable th) {
			throw new StoredException("Cannot start connection", th);
		}
	}

	/* (non-Javadoc)
	 * @see lnzone.lib.p2p.IConnection#setPeerName(java.lang.String)
	 */
	@Override
	public void setPeerName(String name) throws StoredException {
		try {
			LOGGER.debug("Changing connection name from " + peerName + " to " + name);
			this.peerName = Require.notNull(name, "name");
		} catch (Throwable th) {
			throw new StoredException("Cannot set peer name", th);
		}
	}

	/* (non-Javadoc)
	 * @see lnzone.lib.p2p.IConnection#getPeerName()
	 */
	@Override
	public String getPeerName() {
		return this.peerName;
	}

	/* (non-Javadoc)
	 * @see lnzone.lib.p2p.IConnection#requestDisconnect()
	 */
	@Override
	public void requestDisconnect() throws StoredException {
		try {
			socket.close();
		} catch (Throwable th) {
			throw new StoredException("Cannot disconnect connection", th);
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

	@SuppressWarnings("unchecked")
	private void handleResponse(Message message) {
		Require.notNull(message, "message");
		if ("response".equals(message.type) == false) {
			throw new StoredException(
					"Cannot handle response. Given message is not response type, but it is: " + message.type, null);
		}
		LOGGER.debug(peerName + " -> Input type is 'response'");
		LOGGER.debug(peerName + " -> Looking for command related with this input (id = " + message.id + ")");
		@SuppressWarnings("rawtypes")
		Command command = (Command) sentCommands.get(message.id);

		if (command == null) {
			LOGGER.debug(peerName + " -> No command related with this id. Throwing exception");
			throw new StoredException(peerName + " -> Cannot find command with id: " + message.id, null);
		}
		LOGGER.debug(peerName + " -> Found related command: " + command);

		LOGGER.debug(peerName + " -> Synchronizing command");
		synchronized (command) {
			LOGGER.debug(peerName + " -> Inside synchronized block");
			// LOGGER.debug(peerName + " -> Response received for command " + command);
			LOGGER.debug(peerName + " -> Assigning response from json to command");
			if(command instanceof JsonCommand ) {
				JsonCommand cmd = (JsonCommand)command;
				cmd.response = new JsonResponse(message.body);
			} else {
				command.response = (Response) JsonBuilder.build().fromJson(message.body.toString(),
						command.getResponseClass());
			}
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

	@SuppressWarnings("unchecked")
	private void handleError(Message message) {
		Require.notNull(message, "message");
		if ("error".equals(message.type) == false) {
			throw new StoredException("Cannot handle error. Given message is not error type but it is: " + message.type,
					null);
		}
		LOGGER.debug(peerName + " -> Input type is 'error'");
		@SuppressWarnings("rawtypes")
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
			// LOGGER.debug(peerName + " -> Response received for command " + command);
			LOGGER.debug(peerName + " -> Assigning error from json to command");

			// LOGGER.debug(peerName + " -> Error received for command " + command);
			command.error = JsonBuilder.build().fromJson(message.body.toString(), ErrorResponse.class);
			LOGGER.debug(peerName + " -> Error added to command " + command);

			LOGGER.debug(peerName + " -> Waiting for notify");
			command.notify();
			LOGGER.debug(peerName + " -> Notified ");
		}
	}
	
	private Command<Request, Response> recreateCommand(Message message) throws ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		LOGGER.debug(peerName + " -> Craeting new command from stub: " + message.name);
		Class<?> clazzCmd = Class.forName(message.name);
		Method method = clazzCmd.getMethod("createStub");

		@SuppressWarnings("unchecked")
		Command<Request, Response> command = (Command<Request, Response>) method.invoke(null);
		command.setTimeout(message.timeout);
		LOGGER.debug(peerName + " -> Command created from stub: " + command);
		
		return command;
	}
	
	private Message checkForAuthenticated(Message message, Command<Request, Response> command) {
		LOGGER.debug(peerName + " -> Checking for authentication and if given command is IntroduceCommand ");
		if (authenticated == false && ((Command<?, ?>) command instanceof IntroduceCommand == false)) {
			LOGGER.debug(peerName
					+ " -> Not authenticated for command not Introduce. Preparing 'You are not authenticated' error response.");
			ErrorResponse err = new ErrorResponse(0, "Cannot execute command " + command.type
					+ ". You are not authenticated. Call IntroduceCommand first");

			LOGGER.debug(peerName + " -> Create new message with error to sent later on");
			return new Message(message.id, command.getClass().getCanonicalName(), err);
		} else {
			return null;
		}
	}

	private void handleRequest(Message message) {
		Require.notNull(message, "message");
		if ("request".equals(message.type) == false) {
			throw new StoredException(
					"Cannot handle request. Given message is not request type but it is: " + message.type, null);
		}
		LOGGER.debug(peerName + " -> Input type is 'request'");
		try {

			Command<Request, Response> command = recreateCommand(message);

			Message response = checkForAuthenticated(message, command);
			
			if(response == null) { // This means - we are authenticated
				LOGGER.debug(peerName + " -> We are authenticated");
				LOGGER.debug(peerName + " -> Reading request from json");
				Request r = (Request) JsonBuilder.build().fromJson(message.body.toString(), command.getRequestClass());
				command.request = r;
				try {
					long time = (new Date()).getTime();
					LOGGER.debug(peerName + " -> Createing new thread for process command");
					Thread th = new Thread(() -> {
						try {
							LOGGER.debug(peerName + " -> Call listeners for command");
							listener.commandReceived(peerName, command);
							LOGGER.debug(peerName + " -> Command processed without errors");
						} catch (Exception e) {
							LOGGER.debug(peerName + " -> Command processed with error: " + e.getMessage());
							StoredException sex = new StoredException("Cannot process command " + command.type, e);
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
						throw new Exception("Command " + command.type + " execution timeout (took long than "
								+ command.getTimeout() + " milisec");
					}
				} catch (Exception ex) {
					LOGGER.debug(peerName + " -> Exception catched for request processing: " + ex.getMessage());
					StoredException p2pEx = new StoredException("listener.commandReceived thrown: " + ex.getMessage(),
							ex);
					LOGGER.debug(peerName + " -> Assigning given exception to command.error");
					command.error = new ErrorResponse(p2pEx.getMessage(), p2pEx.getId());
				}

				LOGGER.debug(peerName + " -> Checking if command contains response or error");
				if (command.response == null && command.error == null) {
					LOGGER.debug(peerName + " -> Command not contains response or error. Throwing exception");
					command.error = new ErrorResponse(
							"Internal server error. Command was not correctly handled. Listener didn't added response or error to command",
							0);
					// throw new Exception(
					// peerName + " -> Listener didn't added response or error to command: " +
					// command.type);
				}

				LOGGER.debug(peerName + " -> Checking if command contains both response and error");
				if (command.response != null && command.error != null) { // TODO:
																			// Warning
																			// only?
					LOGGER.debug(peerName + " -> Command contains both response and error: " + command.type);

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
				// LOGGER.debug(peerName + " -> Sending back response for command " + command);
			}
			LOGGER.debug(peerName + " -> Sending reply: " + response);
			sendObject(response);
			listener.responseSent(peerName, command);
			LOGGER.debug(peerName + " -> Response sent for command " + command);
		} catch (Exception ex) {
			LOGGER.debug(peerName + " ->Exception thrown. Throw it higher");
			// executionThrownException = ex;
			throw new StoredException("Handle request faild", ex); // TODO: Is this OK?
		}
	}

	private class CommandExecThread extends Thread {

		private final String input;

		public CommandExecThread(final String input) {
			this.input = Require.notEmpty(input, "input");
		}

		@Override
		public void run() {
			Message message = null;
			try {
				message = JsonBuilder.build().fromJson(input, Message.class);
			} catch (Exception ex) {
				new StoredException("Cannot read input message: " + input, ex);
			}

			if (message == null) {
				new StoredException("Null message genarated from json: " + input, null);
				return;
			}

			try {
				LOGGER.debug(peerName + " -> Checkin what is input type");
				if ("response".equals(message.type)) {
					handleResponse(message);
					LOGGER.debug(peerName + " -> Out of handleResponse method");
				} else if ("error".equals(message.type)) {
					handleError(message);
				} else if ("request".equals(message.type)) {
					handleRequest(message);
				} else {
					LOGGER.debug(peerName + " -> Unknown object received. Throwing exception");
					new Exception("Unknown object received: " + message
							+ ". Only Commands and responses and errors are allowed");
					return;
				}
			} catch (Exception ex) {
				ErrorResponse err = new ErrorResponse(0, "Unrecognized error: " + ex.getMessage());

				sendObject(new Message(message.id, "command.getClass().getCanonicalName()", err));
			}

		}

	}

	private class ListeningThread extends Thread {

		public void run() {
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				out = new PrintWriter(socket.getOutputStream(), true);

				synchronized (this) {
					started.set(true);
					notifyAll();
				}

				while (true) {
					try {

						String input = in.readLine();

						if (input == null) {
							LOGGER.info(peerName + " -> NULL input. Stream finished.");
							return;
						}

						threadPool.submit(new CommandExecThread(input));

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
						if (ex instanceof SocketException) {
							LOGGER.debug(peerName + " -> OUT OF LOOP!");
							throw ex;
						} else {
							new StoredException("Exception in main loop", ex);
						}
					}
				}

			} catch (SocketException sex) {
				LOGGER.debug(peerName + " -> Socket exception thrown. Checking if started is set to false");
				if (started.get() == false) {
					LOGGER.debug(peerName + " -> Yes. Started set to false. This is ok. Socket was just closed");
				} else {
					LOGGER.debug(peerName + " -> No. Started set to true. Print exception.");
					sex.printStackTrace(); // TODO: Better handling
					LOGGER.error(sex.getMessage());
					thrownException = sex;
				}
			} catch (Exception e) {
				LOGGER.debug(peerName + " -> Other (not socket) Exception thrown. Print exception");
				LOGGER.error((peerName + " -> " + e.toString()));
				e.printStackTrace();
				thrownException = e;
			} finally {
				LOGGER.debug(peerName + " -> Inside finally block for loop");
				LOGGER.debug(peerName + " -> Synchronizing socket");
				synchronized (socket) {
					try {
						LOGGER.debug(peerName + " -> Synchronized. Closing socket");
						closeSocket();
						LOGGER.debug(peerName + " -> Socket close");
					} catch (Exception ex) {
						LOGGER.debug(peerName + " -> Cannot close socket. Print exception");
						ex.printStackTrace();
						thrownException = ex;
					}
				}
				LOGGER.debug(peerName + " -> Calling listener.onDisconnect");
				listener.onDisconnected(peerName);
				LOGGER.debug(peerName + " -> listener.onDisconnect finished");
				// if(autoReconnect) {
				// parentNode.connectAsync(socket.getLocalAddress().toString(),
				// socket.getLocalPort());
				// }
			}
		}
	}

	private synchronized void sendNativeObject(Message messageWithId) throws StoredException {
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
//				LOGGER.info(peerName + " -> OUTPUT: " + output);
				out.println(output);
			} catch (Exception e) {
				e.printStackTrace();
				LOGGER.error(e.toString());
				closeSocket();
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

	private synchronized void sendObject(Command<?, ?> command) throws StoredException {
		try {
			Require.notNull(command, "command");
			Message o = new Message(0, command);
			o.id = addCommand(command);
			sendNativeObject(o);
		} catch (Throwable th) {
			throw new StoredException("Cannot send command object: " + command, th);
		}
	}

	private synchronized void sendObject(Message response) throws StoredException {
		try {
			Require.notNull(response, "response");
			sendNativeObject(response);
		} catch (Throwable th) {
			throw new StoredException("Cannot send MessageWithId object: " + response, th);
		}
	}

	public void closeSocket() throws StoredException {
		try {
			started.set(false);
			if (socket != null) {
				socket.close();
			}
		} catch (Throwable th) {
			th.printStackTrace();
			LOGGER.error(th.toString());
			throw new StoredException("Cannot close socket: " + socket, th);
		}
	}

	/* (non-Javadoc)
	 * @see lnzone.lib.p2p.IConnection#execute(lnzone.lib.p2p.Command)
	 */
	@Override
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
				sendObject(command);
				LOGGER.debug("Waiting for response to command: " + command);

				// command.wait();
				long time = (new Date()).getTime();
				command.wait(timeout);
				long delta = (new Date()).getTime() - time;
				if (delta >= timeout) {
					throw new Exception("Timeout occured waiting for reply from " + peerName);
				}
				if (command.response == null && command.error == null) {
					throw new Exception(peerName + " -> Command was unlocked without result: " + command);
				}
			}
			LOGGER.debug("Command received: " + command);
		} catch (Throwable th) {
			throw new StoredException("Cannot execute command: " + command, th);
		}

	}

	private void disconnectWithPeer() throws StoredException {
		if (authenticated == false) {
			// no need to call "Disconnect" command. Socket just will be closed
			// in "close" method.
			return;
		}
		DisconnectCommand disconnectCmd = new DisconnectCommand();
		execute(disconnectCmd);
		if (disconnectCmd.error != null) {
			LOGGER.warn("Peer didnt accepted disconnection: " + disconnectCmd);
		}
	}

	/* (non-Javadoc)
	 * @see lnzone.lib.p2p.IConnection#close()
	 */
	@Override
	public void close() throws StoredException {

		if (socket != null) {
			synchronized (socket) {
				if (!socket.isClosed()) {
					disconnectWithPeer();
					threadPool.shutdown();
					try {
						threadPool.awaitTermination(5, TimeUnit.SECONDS);
					} catch (InterruptedException ex) {
						new StoredException("Awaiting termination for all command failed", ex);
					}
					closeSocket();
				}
			}
		}

		try {
			LOGGER.info(peerName + ": Joining thread");
			listeningThread.join();
			LOGGER.info(peerName + ": Joined");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (thrownException != null) {
			throw new StoredException(
					peerName + " -> Socket was closed, but during runtime, connection thrown exception",
					thrownException);
		}

	}

	@Override
	public synchronized void setAuthenticated() {
		this.authenticated = true;
	}

	@Override
	public boolean isAuthenticated() {
		return this.authenticated;
	}

}