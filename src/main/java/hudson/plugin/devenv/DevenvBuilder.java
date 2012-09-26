package hudson.plugin.devenv;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.stapler.DataBoundConstructor;

public class DevenvBuilder extends Builder {

	private final String cmdInit;
	private final String solutionPath;
	private final String buildType;

	@DataBoundConstructor
	public DevenvBuilder(String cmdInit, String solutionPath, String buildType) {
		this.cmdInit = cmdInit;
		this.solutionPath = solutionPath;
		this.buildType = buildType;
	}
	
	public String getCmdInit() {
		return cmdInit;
	}
	
	public String getSolutionPath() {
		return solutionPath;
	}
	
	public String getBuildType() {
		return buildType;
	}

	@Override
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		listener.getLogger().println("building solution at " + solutionPath);
		
		try
	    {
	        final Process p = Runtime.getRuntime().exec("cmd");
	        ProcessOutputReader stderr = new ProcessOutputReader(p.getErrorStream(), listener);
	        final Pattern pattern = Pattern.compile(" (\\d+) error");
	        ProcessOutputReader stdout = new ProcessOutputReader(p.getInputStream(), listener) {
				@Override
				public void onLineRead(String line) {
					Matcher matcher = pattern.matcher(line);
					if (matcher.find()) {
						try {
							int errors = Integer.parseInt(matcher.group(1));
							if (errors > 0) {
								listener.getLogger().println("\n\n!!! ERRORS FOUND: " + errors + " at '" + line + "'\n\n");
								p.destroy();
								Process killer = Runtime.getRuntime().exec("taskkill /F /IM devenv.exe");
								int result = killer.waitFor();
								listener.getLogger().println("taskkill@devenv.exe=" + result);
								
								killer = Runtime.getRuntime().exec("taskkill /F /IM cl.exe");
								result = killer.waitFor();
								listener.getLogger().println("taskkill@cl.exe=" + result);
							}
						} catch (Exception e) {
							// skip
						}
					}
					super.onLineRead(line);
				}
	        };
	        PrintWriter cmdWriter = new PrintWriter(p.getOutputStream());
	        
	        ExecutorService threadPool = Executors.newFixedThreadPool(2);
	        threadPool.submit(stdout);
	        threadPool.submit(stderr);
	        
	        cmdWriter.println("chcp 65001");
	        cmdWriter.println(cmdInit);
	        cmdWriter.println(String.format("devenv %s /build %s", solutionPath, buildType));
	        cmdWriter.close();
	        final int exitValue = p.waitFor();
	        Thread.sleep(5000); // give threads some time to terminate
	        if (exitValue == 0) {
	            // System.out.print(stdout.toString());
	        } else {
	            // System.err.print(stderr.toString());
	        }
	    }
	    catch (final IOException e) {
	        throw new RuntimeException(e);
	    } catch (final InterruptedException e) {
	        throw new RuntimeException(e);
	    }
		
		return true;
	}



	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {
		@Override
		@SuppressWarnings("unchecked")
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return FreeStyleProject.class.isAssignableFrom(jobType);
		}

		@Override
		public String getDisplayName() {
			return "Execute Devenv task";
		}
		
	}
}
