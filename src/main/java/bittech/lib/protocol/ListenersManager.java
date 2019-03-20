package bittech.lib.protocol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class ListenersManager {

	private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

	private Map<Class<?>, Listener> listeners = new HashMap<Class<?>, Listener>();
	// private Set<Listener> listeners = new HashSet<Listener>();
	private Set<ConnectionListener> connectionListeners = new HashSet<ConnectionListener>();

	public ListenersManager() {
		LOGGER.debug("Createing ManagingListener");
	}

	public synchronized void registerListener(Listener listener) {
		LOGGER.debug("Registering listener: " + listener.getClass().getSimpleName());
		
		Require.notNull(listener, "listener");
		
		if (listeners.values().contains(listener)) {
			throw new StoredException("Listener already registered: " + listener, null);
		}
		
		if(listener.getListeningCommands() == null) {
			throw new StoredException("getListeningCommands method for Listener " + listener.getClass() + " returns null instead of commands list", null);
		}
		
		for (Class<?> clazz : listener.getListeningCommands()) {
			if (listeners.put(clazz, listener) != null) {
				throw new StoredException("Listnere for " + clazz + " was added before.", null);
			}
		}

		LOGGER.debug("Listener registered: " + listener.getClass().getSimpleName());
		LOGGER.debug("Number of listeners: " + listeners.size());
	}

	public synchronized void unregisterListener(Listener listener) {
		for (Class<?> clazz : listener.getListeningCommands()) {
			listeners.remove(clazz);
		}
	}

	public synchronized void registerConnectionListener(ConnectionListener listener) {
		LOGGER.debug("Registering connection listener: " + listener.getClass().getSimpleName());
		if (connectionListeners.contains(listener)) {
			throw new RuntimeException("Connection listener already registered: " + listener);
		}
		connectionListeners.add(listener);
		LOGGER.debug("Connection listener registered: " + listener.getClass().getSimpleName());
		LOGGER.debug("Number of connection listeners: " + connectionListeners.size());
	}

	public synchronized void unregisterConnectionListener(ConnectionListener listener) {
		if (connectionListeners.remove(listener) == false) {
			throw new RuntimeException(
					"Listener scheduked to unregister not on connection listeners list: " + listener);
		}
	}

	public void onConnected(String peerName) {
		LOGGER.debug("on Connected: " + peerName);
		for (ConnectionListener listener : connectionListeners) {
			listener.onConnected(peerName);
		}
	}

	public synchronized void onDisconnected(String peerName) {
		LOGGER.info("On disconnected: " + peerName);
		for (ConnectionListener listener : connectionListeners) {
			try {
				listener.onDisconnected(peerName);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	private boolean containsService(Listener listener, String serviceName) {
		if (listener.getListeningServices() == null) {
			return true;
		} else {
			for (String sName : listener.getListeningServices()) {
				if (serviceName.equals(sName)) {
					return true;
				}
			}
		}
		return false;
	}

	private synchronized Listener findListener(String serviceName, Class<?> commandClass) {
		LOGGER.debug("Looking for listeners: " + listeners.size());
		Listener listener = listeners.get(commandClass);
		if (listener == null) {
			throw new StoredException("This node has no listener for command " + commandClass, null);
		}
		if (containsService(listener, serviceName)) {
			return listener;
		}

		throw new RuntimeException(
				"Cannot find listener for service " + serviceName + " and class " + commandClass.getSimpleName());
	}

	public synchronized void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException {
		findListener(fromServiceName, command.getClass()).commandReceived(fromServiceName, command);
	}
	
	public synchronized void responseSent(String fromServiceName, Command<?, ?> command) throws StoredException {
		findListener(fromServiceName, command.getClass()).responseSent(fromServiceName, command);
	}

	public synchronized Set<Class<?>> getAllListeningCommands() {
		return new HashSet<Class<?>>(listeners.keySet());
	}

}
