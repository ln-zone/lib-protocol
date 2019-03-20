package bittech.lib.protocol.connections;

import bittech.lib.protocol.Command;
import bittech.lib.utils.exceptions.StoredException;

public interface IConnection extends AutoCloseable {

	void setStarted(boolean started);

	void setPeerName(String name) throws StoredException;

	String getPeerName();

	void requestDisconnect() throws StoredException;

	void execute(Command<?, ?> command) throws StoredException;

	void close() throws StoredException;
	
	void setAuthenticated();
	
	boolean isAuthenticated();

}