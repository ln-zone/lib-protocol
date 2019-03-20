package bittech.lib.protocol;

import bittech.lib.utils.exceptions.StoredException;

public interface Listener {

	/**
	 * @return List of listening comamnds or null if listenieng every command type
	 */
	public Class<?>[] getListeningCommands();

	/**
	 * 
	 * @return List of listening services or null if listening every service
	 */
	public String[] getListeningServices();

	/**
	 * Called when command meets requirements related with with listening command
	 * types and services names
	 * 
	 * @param fromServiceName
	 * @param command
	 */
	public void commandReceived(String fromServiceName, Command<?, ?> command) throws StoredException;

	public void responseSent(String serviceName, Command<?, ?> command);
	
}
