package bittech.lib.protocol;

import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class Connection {

	private Node node;
	private String connectionName;

	public Connection(Node node, String connectionName) {
		this.node = Require.notNull(node, "node");
		this.connectionName = Require.notNull(connectionName, "connectionName");
	}

	public void execute(Command<?, ?> command) throws StoredException {
		node.execute(connectionName, Require.notNull(command, "command"));
	}

	public Node getNode() {
		return node;
	}

}
