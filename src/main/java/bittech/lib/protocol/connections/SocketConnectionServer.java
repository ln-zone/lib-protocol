package bittech.lib.protocol.connections;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.protocol.ListenersManager;
import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class SocketConnectionServer extends Thread implements AutoCloseable {

	private final static Logger LOGGER = LoggerFactory.getLogger(SocketConnectionServer.class);

	private ServerSocket ss = null;

	private final int listeningPort;

	private AtomicBoolean started = new AtomicBoolean(true);

	private final ListenersManager listenersManager;
	private final ConnectionsManager peersConnectionsManager;

	public SocketConnectionServer(final int listeningPort, final ListenersManager listenersManager,
			final ConnectionsManager peersConnectionsManager) {
		this.listeningPort = Require.inRange(listeningPort, 1, Short.MAX_VALUE, "listeningPort");
		this.listenersManager = Require.notNull(listenersManager, "listenersManager");
		this.peersConnectionsManager = Require.notNull(peersConnectionsManager, "peersConnectionsManager");
	}

	public void run() {
		try {
			try {
				ss = new ServerSocket(listeningPort);
			} catch (Exception ex) {
				throw new StoredException("Cannot create server socket for port " + listeningPort, ex);
			}
			synchronized (this) {
				notify();
			}
			while (started.get()) {
				LOGGER.debug("Waiting for connection");
				Socket s = ss.accept();
				LOGGER.debug("New connection accepted");
				SocketConnection con = new SocketConnection(listenersManager,
						"AUTONAME:" + (long) (Math.random() * Long.MAX_VALUE));
				peersConnectionsManager.addPeerConnection(con);
				con.start(s);
			}
		} catch (SocketException sex) {
			if (started.compareAndSet(false, false)) {
			} else {
				started.set(true);
				System.err.println("Failed for port:" + listeningPort);
				sex.printStackTrace(); // TODO: Better handling
				LOGGER.error(sex.getMessage());
			}
		} catch (Exception e) {
			e.printStackTrace();
			new StoredException("Server thread failed", e);
			try {
				if (ss != null) {
					ss.close();
				}
			} catch (IOException e1) {
				new StoredException("Cannot close socket", e1);
			}
		}
	}

	@Override
	public void close() throws Exception {
		started.set(false);
		if (ss != null) {
			ss.close();
		}
	}
}
