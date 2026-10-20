package com.jaredscarito.resourceeventscrambler.main;

class ScrambleException extends Exception {
	ScrambleException(String message) {
		super(message);
	}

	ScrambleException(String message, Throwable cause) {
		super(message, cause);
	}
}
