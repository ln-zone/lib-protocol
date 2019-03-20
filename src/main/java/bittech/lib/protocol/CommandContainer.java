package bittech.lib.protocol;

import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class CommandContainer {

	public final Command<?, ?> command;
	public final long commandId;

	private boolean executed;

	public CommandContainer(final long commandId, final Command<?, ?> command) throws StoredException {
		try {
			this.commandId = commandId;
			this.command = Require.notNull(command, "command");
		} catch (Throwable th) {
			throw new StoredException("Cannot create command container", th);
		}
	}

	public synchronized boolean isExecuted() {
		return executed;
	}

	public synchronized void setExecuted(boolean executed) {
		this.executed = executed;
	}

}
