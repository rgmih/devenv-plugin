package hudson.plugin.devenv;

import hudson.model.BuildListener;

import java.io.InputStream;

public class ProcessErrorReader extends ProcessOutputReader {

	public ProcessErrorReader(InputStream in, BuildListener listener) {
		super(in, listener);
	}

	private final StringBuilder message = new StringBuilder();
	
	@Override
	public void onLineRead(String line) {
		message.append(line).append("\n");
	}
	
	public String getErrorMessage() {
		return message.toString();
	}
}
