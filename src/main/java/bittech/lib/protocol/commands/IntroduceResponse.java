package bittech.lib.protocol.commands;

import bittech.lib.protocol.Response;

public class IntroduceResponse implements Response {

	public boolean accepted;
	public String responderName;

	public IntroduceResponse(boolean accepted, String responderName) {
		this.accepted = accepted;
		this.responderName = responderName;
	}

}
