package bittech.lib.protocol.connections;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.protocol.Command;
import bittech.lib.protocol.ConnectionListener;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.ListenersManager;
import bittech.lib.protocol.commands.DisconnectCommand;
import bittech.lib.protocol.commands.DisconnectResponse;
import bittech.lib.protocol.commands.IntroduceCommand;
import bittech.lib.protocol.commands.IntroduceResponse;
import bittech.lib.utils.Config;
import bittech.lib.utils.Crypto;
import bittech.lib.utils.Require;
import bittech.lib.utils.RsaKeys;
import bittech.lib.utils.exceptions.StoredException;
import bittech.lib.utils.json.JsonBuilder;

public class ConnectionsManager implements Listener, ConnectionListener {

	private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

	private Map<String, IConnection> connections = new ConcurrentHashMap<String, IConnection>();

	private String nodeName;

	final ListenersManager listenersManager;

	private String myPrvKey = Config.getInstance().getEntry("connectionKeys", RsaKeys.class).getPrv();

	public ConnectionsManager(String nodeName, final ListenersManager listenersManager) {
		this.nodeName = nodeName;
		this.listenersManager = Require.notNull(listenersManager, "listenersManager");
	}

	public String toString() {
		// synchronized (connections) {
		return (JsonBuilder.build()).toJson(connections.keySet());
		// }
	}

	@Override
	public synchronized Class<?>[] getListeningCommands() {
		return new Class[] { IntroduceCommand.class, DisconnectCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public void commandReceived(String peerName, Command<?, ?> command) {
		try {
			LOGGER.debug("Command came for service " + peerName + ". Command: " + command);
			if (command instanceof IntroduceCommand) {
				IntroduceCommand cmd = (IntroduceCommand) command;

				try {
					String name = Crypto.decryptText(cmd.getRequest().peerPubEncryptedName, myPrvKey);
					if (!name.equals(cmd.getRequest().serviceName)) {
						throw new Exception(
								"Introduce authentication failes. Wrong public key used to encrypt message");
					}
				} catch (Exception ex) {
					throw new Exception("Authentication failed. Cannot decrypt message: '"
							+ cmd.getRequest().peerPubEncryptedName + " using my private key", ex);
				}

				replaceConnectionName(peerName, cmd.request.serviceName);
				IConnection connection = connections.get(cmd.request.serviceName);
				connection.setAuthenticated();
				
				cmd.response = new IntroduceResponse(true, nodeName);
			} else if (command instanceof DisconnectCommand) {
				LOGGER.info("Disconnect command received from " + peerName + ". Command: " + command);
				DisconnectCommand cmd = (DisconnectCommand) command;
				onDisconnected(peerName);
				cmd.response = new DisconnectResponse(true);
			} else {
				throw new RuntimeException("Unsupported command: " + command);
			}
		} catch (Throwable th) {
			throw new RuntimeException(
					"Cannot response to command received: " + command + " for peer name: " + peerName, th);
		}
	}
	
	@Override
	public void responseSent(String peerName, Command<?, ?> command) {
		if (command instanceof IntroduceCommand) {
			IntroduceCommand cmd = (IntroduceCommand) command;
			if (cmd.getError() == null && cmd.getResponse() != null) {
				listenersManager.onConnected(cmd.request.serviceName);
			}
		}
	}

	public synchronized IConnection getConnection(String connectionName) throws StoredException {
		try {
			// synchronized (connections) {
			return connections.get(connectionName);
			// }
		} catch (Throwable th) {
			throw new StoredException("Cannot get connection based on connectionName: " + connectionName, th);
		}
	}

	public synchronized void addPeerConnection(SocketConnection connection) throws StoredException {
		try {
			if (connection.getPeerName() == null) {
				throw new RuntimeException("Cannot add peer without name");
			}
			LOGGER.debug("Adding peer with name: " + connection.getPeerName());
			// synchronized (connections) {
			connections.put(connection.getPeerName(), connection);
			// }
		} catch (Throwable th) {
			throw new StoredException("Cannot add peer connection", th);
		}
	}

	public synchronized void replaceConnectionName(String oldName, String newName) throws StoredException {
		try {
			// synchronized (connections) {
			IConnection com = connections.get(oldName);

			if (com == null) {
				throw new RuntimeException(
						"Cannot change connection name. Given old name (" + oldName + ") is not in communicators map");
			}

			if (com.getPeerName().startsWith("AUTONAME")) {

				com.setPeerName(newName);
				connections.remove(oldName);
				LOGGER.debug("Deleting peer with name: " + oldName);
				connections.put(com.getPeerName(), com);
				LOGGER.debug("Adding peer with name: " + com.getPeerName());
			}
			// }
		} catch (Throwable th) {
			throw new StoredException("Cannot replace connection name from " + oldName + " to " + newName, th);
		}
	}

	public synchronized void deletePeerConnection(IConnection newPeer) throws StoredException {
		try {
			LOGGER.info("Deleting peer with name: " + newPeer);
			// synchronized (connections) {
			connections.remove(newPeer.getPeerName());
			// }
		} catch (Throwable th) {
			throw new StoredException("Cannot delete peer conenction", th);
		}
	}

	public void closeAllConnections() throws StoredException {
		// synchronized (connections) {
		for (IConnection connection : connections.values()) {
			connection.close();
			// }
		}

	}

	@Override
	public void onDisconnected(String peerName) throws StoredException {
		LOGGER.debug("ConnectionsManager.onDisconnected");
		try {
			// synchronized (connections) {
			IConnection connection = connections.get(peerName);

			if (connection == null) {
				LOGGER.debug("Disonnected before");
				return;
			}
			LOGGER.info("Stopping connection with peer " + peerName);
			connection.setStarted(false);
			connections.remove(peerName);
			// }
		} catch (Throwable th) {
			throw new StoredException("Cannot disconnect: + peerName", th);
		}
	}

	@Override
	public void onConnected(String peerName) {
		// Nothing to do
	}
	
	public synchronized List<String> listConnectionNames() {
		List<String> ret = new ArrayList<String>(connections.keySet().size());
		for(String connectionName : connections.keySet()) {
			ret.add(connectionName);
		}
		return ret;
	}

}
