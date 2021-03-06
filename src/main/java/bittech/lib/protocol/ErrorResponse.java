package bittech.lib.protocol;

import bittech.lib.utils.Require;
import bittech.lib.utils.exceptions.StoredException;

public class ErrorResponse {

	public final long errorCode;
	public final String type;
	public final String message;
	public final long exceptionId;

	public final ErrorResponse cause;

	public ErrorResponse(Throwable exception) {
		this.errorCode = 0;
		this.message = exception.getMessage();
		this.type = exception.getClass().getName();
		if (exception instanceof StoredException) {
			this.exceptionId = ((StoredException) exception).getId();
		} else {
			this.exceptionId = 0;
		}

		if (exception.getCause() == null) {
			this.cause = null;
		} else {
			this.cause = new ErrorResponse(exception.getCause());
		}
	}
	
	public boolean containsMessage(String message) {
		ErrorResponse r = this;
		while(r != null) {
			if(r.message != null && r.message.contains(message)) {
				return true;
			}
			r = r.cause;
		}
		return false;
	}
	
	public ErrorResponse getCause(int deep) {
		ErrorResponse cause = this;
		for(int i = 0; i< deep; i++) {
			cause = cause.cause;
		}
		return cause;
	}
	
	public ErrorResponse findWithMessage(String message) {
		ErrorResponse r = this;
		while(r != null) {
			if(r.message != null && r.message.contains(message)) {
				return r;
			}
			r = r.cause;
		}
		return null;
	}

	public ErrorResponse(long errorCode, String message, long exceptionId) {
		this.errorCode = errorCode;
		this.type = "message";
		this.message = Require.notNull(message, "message");
		this.exceptionId = exceptionId;
		this.cause = null;
	}

	public ErrorResponse(long errorCode, String message) {
		this.errorCode = errorCode;
		this.type = "message";
		this.message = Require.notNull(message, "message");
		this.exceptionId = -1;
		this.cause = null;
	}

	public ErrorResponse(String message, long exceptionId) {
		this.errorCode = 0;
		this.type = "message";
		this.message = Require.notNull(message, "message");
		;
		this.exceptionId = exceptionId;
		this.cause = null;
	}

	@Override
	public String toString() {
		return "erorrCode: " + errorCode + ", message: '" + message + ", exceptionId: " + exceptionId;
	}

	public Exception toException() {
		if (this.cause == null) {
			return new Exception(this.message);
		} else {
			return new Exception(this.message, this.cause.toException());
		}
	}

}
