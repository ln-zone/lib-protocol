package bittech.lib.protocol.commands;

import bittech.lib.protocol.Command;
import bittech.lib.utils.Require;

public class IntroduceCommand extends Command<IntroduceRequest, IntroduceResponse> {

	public static IntroduceCommand createStub() {
		return new IntroduceCommand("", "");
	}

	public IntroduceCommand(String serviceName, String peerPubEncryptedName) {
		this.request = new IntroduceRequest();
		this.request.serviceName = Require.notNull(serviceName, "serviceName");
		this.request.peerPubEncryptedName = Require.notNull(peerPubEncryptedName, "peerPubEncryptedName");
		timeout = 15000;
	}

}