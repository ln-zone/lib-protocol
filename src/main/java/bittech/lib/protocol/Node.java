package bittech.lib.protocol;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.protocol.commands.IntroduceCommand;
import bittech.lib.protocol.connections.ConnectionsManager;
import bittech.lib.protocol.connections.IConnection;
import bittech.lib.protocol.connections.SocketConnection;
import bittech.lib.protocol.connections.SocketConnectionServer;
import bittech.lib.utils.Config;
import bittech.lib.utils.Crypto;
import bittech.lib.utils.Require;
import bittech.lib.utils.RsaKeys;
import bittech.lib.utils.exceptions.StoredException;

public class Node implements AutoCloseable {

	private final static Logger LOGGER = LoggerFactory.getLogger(Node.class);

	private static int connectionTimeoutMilisec = 3000;

	private final SocketConnectionServer socketConnectionServer;

	private final ListenersManager listenersManager = new ListenersManager();

	private final ConnectionsManager peersConnectionsManager;

	private final ToReconnectListener toReconnect;

	private final String myName;

	public Set<Class<?>> getListenedCommands() {
		return listenersManager.getAllListeningCommands();
	}

	public Node(String myName, int listeningPort) throws StoredException {
		super();
		try {
			LOGGER.debug("Createing Node \"" + myName + "\" listening on port " + listeningPort);

			
			this.myName = Require.notNull(myName, "myName");
			this.peersConnectionsManager = new ConnectionsManager(myName, this.listenersManager);
			this.listenersManager.registerListener(this.peersConnectionsManager);
			this.listenersManager.registerConnectionListener(this.peersConnectionsManager);
			
			this.socketConnectionServer = new SocketConnectionServer(listeningPort, listenersManager, peersConnectionsManager);

			this.toReconnect = new ToReconnectListener(this);
			this.listenersManager.registerConnectionListener(this.toReconnect);

			synchronized (this.socketConnectionServer) {
				this.socketConnectionServer.start();
				this.socketConnectionServer.wait();
			}

			LOGGER.debug("Node created");
		} catch (Exception ex) {
			throw new StoredException("Cannot create node '" + myName + "' listening on port " + listeningPort, ex);
		}
	}

	public Node(String myName) {
		LOGGER.debug("Createing non-listening Node \"" + myName + "\"");

		this.socketConnectionServer = null;
		this.myName = Require.notNull(myName, "myName");
		this.peersConnectionsManager = new ConnectionsManager(myName, this.listenersManager);
		this.listenersManager.registerListener(this.peersConnectionsManager);
		this.listenersManager.registerConnectionListener(this.peersConnectionsManager);

		this.toReconnect = new ToReconnectListener(this);
		this.listenersManager.registerConnectionListener(this.toReconnect);

		LOGGER.debug("Non-listening Node \"" + myName + "\" created");
	}

	public void registerListener(Listener listener) {
		Require.notNull(listener, "listener");
		listenersManager.registerListener(listener);
	}

	public void unregisterListener(Listener listener) {
		Require.notNull(listener, "listener");
		listenersManager.unregisterListener(listener);
	}

	public void registerConnectionListener(ConnectionListener listener) {
		Require.notNull(listener, "listener");
		listenersManager.registerConnectionListener(listener);
	}

	public void unregisterConnectionListener(ConnectionListener listener) {
		Require.notNull(listener, "listener");
		listenersManager.unregisterConnectionListener(listener);
	}

	public boolean isPeerConnected(String name) throws StoredException {
		IConnection con = peersConnectionsManager.getConnection(name);
		if (con == null) {
			return false;
		}
		if (con.isAuthenticated() == false) {
			return false;
		}
		return true;
	}

	private boolean connect(String peerName, String ip, int port, String peerPubKey, boolean storeException,
			boolean autoReconnect) throws StoredException {
		SocketConnection newPeer = null;
		try {
			LOGGER.debug("Connecting from \"" + myName + "\" to ip " + ip + " on port " + port);
			if (autoReconnect) {
				toReconnect.addConection(peerName, ip, port, peerPubKey);
			}
			Require.notNull(ip, "ip");
			Require.inRange(port, 1, Short.MAX_VALUE, "port");
			newPeer = new SocketConnection(listenersManager, peerName);
			peersConnectionsManager.addPeerConnection(newPeer);
			Socket s1 = new Socket();
			s1.connect(new InetSocketAddress(ip, port), connectionTimeoutMilisec);
			newPeer.start(s1);

			sendIntroduce(newPeer, peerPubKey);
			return true;
		} catch (Throwable th) {
			if (newPeer != null) {
				newPeer.setStarted(false);
				peersConnectionsManager.deletePeerConnection(newPeer);
			}
			if (storeException) {
				throw new StoredException("Cannot connect to: " + ip + " on port " + port, th);
			} else {
				return false;
			}
		}
	}

	public Connection connect(String peerName, String ip, int port) throws StoredException {
		connect(peerName, ip, port, Config.getInstance().getEntry("connectionKeys", RsaKeys.class).getPub());
		return new Connection(this, peerName);
	}

	public Connection connect(String peerName, String ip, int port, String peerPubKey) throws StoredException {
		connect(peerName, ip, port, peerPubKey, true, false);
		return new Connection(this, peerName);
	}

	public Connection connectAsync(String peerName, String ip, int port, String peerPubKey) {
		LOGGER.debug("Connect async called");
		new Thread(() -> {
			while (true) {
				try {
					if (connect(peerName, ip, port, peerPubKey, false, true)) {
						LOGGER.info("CONNECTED TO: " + ip + ":" + port);
						return;
					} else {
						LOGGER.info("Connection to " + ip + ":" + port + " failed. Retraing.");
						Thread.sleep(1000);
					}
				} catch (Exception e) {
					e.printStackTrace(); // TODO: Better handling?
				}
			}
		}).start();
		return new Connection(this, peerName);
	}

	public Connection connectAsync(String peerName, String ip, int port) {
		return connectAsync(peerName, ip, port, Config.getInstance().getEntry("connectionKeys", RsaKeys.class).getPub());
	}

	public Connection connectWithReconnect(String peerName, String ip, int port) throws StoredException {
		connect(peerName, ip, port, Config.getInstance().getEntry("connectionKeys", RsaKeys.class).getPub(), true,
				true);
		return new Connection(this, peerName);
	}

	private void sendIntroduce(SocketConnection newPeer, String peerPubKey) throws Exception {
		try {
			Require.notNull(newPeer, "newPeer");
			IntroduceCommand introduceCmd = new IntroduceCommand(myName, Crypto.encryptText(myName, peerPubKey));
			newPeer.execute(introduceCmd);
			if (introduceCmd.getError() != null) {
				LOGGER.error("Server didn't accept connection for service \"" + myName + "\"");
				throw new StoredException("Connection refused." + introduceCmd.getError(), null);
			}
			if (introduceCmd.response.accepted == false) {
				LOGGER.error("Server didn't accept connection for service \"" + myName + "\". Responder: " + "\""
						+ introduceCmd.response.responderName + "\"");
				throw new StoredException("Connection refused.", null);
			} else {
				peersConnectionsManager.replaceConnectionName(newPeer.getPeerName(),
						introduceCmd.response.responderName);
				listenersManager.onConnected(newPeer.getPeerName());
				newPeer.setAuthenticated();
				LOGGER.debug("\"" + newPeer.getPeerName() + "\" accepted connection with \"" + myName + "\".");
			}
		} catch (Exception ex) {
			throw new StoredException("Failed when trying to authenticate to " + newPeer.getPeerName(), ex);
		}
	}

	@Override
	public void close() throws StoredException {
		toReconnect.removeAll();
		try {
			if (this.socketConnectionServer != null) {
				this.socketConnectionServer.close();
				LOGGER.info(myName + " -> Listening socket closed");
			}

			peersConnectionsManager.closeAllConnections();
		} catch (Throwable th) {
			throw new StoredException("Node didn't closed properly: ", th);
		}
	}

	public void execute(String connectionName, Command<?, ?> command) throws StoredException {
		try {
			Require.notNull(connectionName, "connectionName");
			Require.notNull(command, "command");
			Require.notNull(command.request, "request");
			IConnection com = peersConnectionsManager.getConnection(connectionName);
			if (com == null) {
				throw new Exception("No connection found for name: " + connectionName);
			}
			com.execute(command);
		} catch (Throwable th) {
			throw new StoredException("Cannot execute command for connection " + connectionName + "Command: " + command,
					th);
		}
	}
	
//	public void executeJson(String connectionName, String request) throws StoredException {
//		try {
//			Require.notNull(connectionName, "connectionName");
//			Require.notNull(command, "command");
//			Require.notNull(command.request, "request");
//			IConnection com = peersConnectionsManager.getConnection(connectionName);
//			if (com == null) {
//				throw new Exception("No connection found for name: " + connectionName);
//			}
//			com.execute(command);
//		} catch (Throwable th) {
//			throw new StoredException("Cannot execute command for connection " + connectionName + "Command: " + command,
//					th);
//		}
//	}

	public void disconnect(String peerName) throws StoredException {
		try {
			Require.notNull(peerName, "peerName");
			toReconnect.removePeer(peerName);
			IConnection com = peersConnectionsManager.getConnection(peerName);
			if (com == null) {
				throw new Exception("No connection found for name: " + peerName);
			}
			com.close();
		} catch (Throwable th) {
			throw new StoredException("Cannot disconnect peer " + peerName, th);
		}
	}

}
