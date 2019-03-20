package bittech.lib.protocol;

import bittech.lib.utils.exceptions.StoredException;

public interface ConnectionListener {

	public void onConnected(String peerName);

	public void onDisconnected(String peerName) throws StoredException;

}
