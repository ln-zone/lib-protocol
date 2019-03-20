package bittech.lib.protocol;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

/**
 * 
 * Stores all connection requests and call "reconnect" for stored request if
 * disconnected occurs.
 * 
 *
 */
public class ToReconnectListener implements ConnectionListener {

	public static final class ConnectionData {
		public String ip;
		public int port;
		public String pubKey;
	}

	private final Map<String, ConnectionData> connections = new ConcurrentHashMap<String, ConnectionData>();

	private final Node node;

	public ToReconnectListener(Node node) {
		this.node = Require.notNull(node, "node");
	}

	public void addConection(String peerName, String ip, int port, String pubKey) {
		Require.notNull(peerName, "peerName");
		ConnectionData cd = new ConnectionData();
		cd.ip = Require.notNull(ip, "ip");
		cd.port = Require.inRange(port, 1, Short.MAX_VALUE, "port");
		cd.pubKey = Require.notNull(pubKey, "pubKey");

		connections.put(peerName, cd); // TODO: Not add if exists

	}

	@Override
	public void onConnected(String peerName) {
		// Do nothing
	}

	@Override
	public void onDisconnected(String peerName) throws StoredException {
		ConnectionData cd = connections.get(peerName);
		if (cd != null) {
			node.connectAsync(peerName, cd.ip, cd.port, cd.pubKey);
		}

	}

	public void removeAll() {
		connections.clear();
	}

	public void removePeer(String peerName) {
		connections.remove(peerName);
	}

}
