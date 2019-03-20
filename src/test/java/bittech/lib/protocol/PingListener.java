package bittech.lib.protocol;

import bittech.lib.protocol.Command;
import bittech.lib.protocol.ErrorResponse;
import bittech.lib.protocol.Listener;
import bittech.lib.protocol.commands.PingCommand;
import bittech.lib.protocol.commands.PingResponse;

public class PingListener implements Listener {

	private long responseTime = 0;
	private String reponseMessage = null;
	private ErrorResponse reponseError = null;

	public PingListener(long responseTime, String responseMessage) {
		this.responseTime = responseTime;
		this.reponseMessage = responseMessage;
	}

	public PingListener(long responseTime, ErrorResponse reponseError) {
		this.responseTime = responseTime;
		this.reponseError = reponseError;
	}

	// @Override
	public Class<?>[] getListeningCommands() {
		return new Class[] { PingCommand.class };
	}

	@Override
	public String[] getListeningServices() {
		return null;
	}

	@Override
	public void commandReceived(String fromServiceName, Command<?, ?> command) {
		try {
			Thread.sleep(responseTime);
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException("Interrupted");
		}
		if (command instanceof PingCommand) {
			PingCommand pc = (PingCommand) command;
			if (reponseMessage != null) {
				pc.response = new PingResponse(reponseMessage);
			}
			if (reponseError != null) {
				pc.error = reponseError;
			}
		} else {
			throw new RuntimeException("Do not support command: " + command);
		}

	}

	@Override
	public void responseSent(String serviceName, Command<?, ?> command) {
		// Nothing here
	}

}
