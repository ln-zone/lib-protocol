package bittech.lib.protocol;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.google.gson.Gson;

import bittech.lib.utils.json.JsonBuilder;

public abstract class Command<T extends Request, E extends Response> {

	public String type = this.getClass().getCanonicalName();

	public T request;
	public E response;

	public ErrorResponse error = null;

	private String authKey = null;
	
	protected long timeout = 200000;

	@SuppressWarnings("unchecked")
	public final Class<Request> getRequestClass() {
		Type s = this.getClass().getGenericSuperclass();
		Type t = ((ParameterizedType) s).getActualTypeArguments()[0];
		return (Class<Request>) t;
	}

	@SuppressWarnings("unchecked")
	public final Class<Request> getResponseClass() {
		Type s = this.getClass().getGenericSuperclass();
		Type t = ((ParameterizedType) s).getActualTypeArguments()[1];
		return (Class<Request>) t;
	}

	@Override
	public String toString() {
		Gson gson = JsonBuilder.build();
		return gson.toJson(this);
	}

	public T getRequest() {
		return request;
	}

	public E getResponse() {
		return response;
	}

	public ErrorResponse getError() {
		return error;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
	
	public void setAuthKey(String authKey) {
		this.authKey = authKey;
	}

	public String getAuthKey() {
		return authKey;
	}
	// public abstract Class<?> getResponseClass();

}
