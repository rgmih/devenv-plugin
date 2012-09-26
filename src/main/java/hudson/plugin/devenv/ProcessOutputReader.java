package hudson.plugin.devenv;

import hudson.model.BuildListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ProcessOutputReader implements Runnable {

	final InputStream in;
	final BuildListener listener;
	
	public ProcessOutputReader(InputStream in, BuildListener listener) {
		this.in = in;
		this.listener = listener;
	}
	
	public void run() {
		try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line = null;
            while ((line = reader.readLine()) != null)
            {
                onLineRead(line);
            }
        } catch (final IOException ioe) {
            System.err.println(ioe.getMessage());
            throw new RuntimeException(ioe);
        }
	}

	public void onLineRead(String line) {
		this.listener.getLogger().println("> " + line);
	}
}
