package bittech.lib.protocol;

public interface WebDataTrensferredListener {

	abstract void received(String data);

	abstract void sent(String data);

	abstract void broadcasted(String data);
}
