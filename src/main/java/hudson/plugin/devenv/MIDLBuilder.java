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
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.stapler.DataBoundConstructor;

public class MIDLBuilder extends Builder {

	private final String cmdInit;
	private final String taskList;
	private final String path;

	@DataBoundConstructor
	public MIDLBuilder(String cmdInit, String taskList, String path) {
		this.cmdInit = cmdInit;
		this.taskList = taskList;
		this.path = path;
	}
	
	public String getCmdInit() {
		return cmdInit;
	}
	
	public String getTaskList() {
		return taskList;
	}
	
	public String getPath() {
		return path;
	}

	private static class Task {
		private String idl;
		private String out;
		private String path = null;
		
		public Task(String idl, String out) {
			this.idl = idl;
			this.out = out;
			
		}
		
		public Task(String idl, String out, String path) {
			this.idl = idl;
			this.out = out;
			this.path = path;
		}
		
		public String getCommand() {
			String midlCommand = String.format("midl %s /out %s", idl, out);
			if (path != null) {
				return "pushd " + path + " && " + midlCommand + " && popd";
			} else {
				return midlCommand;
			}
		}
	}
	
	@Override
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		String lines[] = taskList.split("\\r?\\n");
		
		ArrayList<Task> tasks = new ArrayList<Task>();
		for (String line : lines) {
			ArrayList<String> tokens = parseTaskLine(line);
			if (tokens.size() == 0) {
				listener.fatalError("invalid task format: '%s'; terminating", line);
				return false;
			} else if (tokens.size() == 1) {
				String idl = tokens.get(0);
				String out = "./";
				int p = Math.max(idl.lastIndexOf("\\"), idl.lastIndexOf("/"));
				if (p > 0) {
					out = idl.substring(0, p + 1);
				}
				tasks.add(new Task(idl, out));
			} else if (tokens.size() == 2) {
				tasks.add(new Task(tokens.get(0), tokens.get(1)));
			} else if (tokens.size() > 2) {
				tasks.add(new Task(tokens.get(0), tokens.get(1), tokens.get(2)));
			}
		}
		
		Process process = Runtime.getRuntime().exec("cmd");
		ProcessOutputReader stderr = new ProcessOutputReader(process.getErrorStream(), listener);
        ProcessOutputReader stdout = new ProcessOutputReader(process.getInputStream(), listener);
        PrintWriter cmd = new PrintWriter(process.getOutputStream());
        
        ExecutorService threadPool = Executors.newFixedThreadPool(2);
        threadPool.submit(stdout);
        threadPool.submit(stderr);
        
        cmd.println("chcp 65001");
        if (path != null && !"".equals(path)) {
        	cmd.println("cd " + path + " || exit");
        }
        cmd.println(cmdInit);
        for (Task task : tasks) {
        	cmd.println("(" + task.getCommand() + " || exit) && echo midl: finished; ok");
        }
        cmd.close();
		
        final int exitValue = process.waitFor();
        
        if (exitValue == 0) {
            return true;
        } else {
            listener.fatalError("last command failed; terminating");
            return false;
        }
	}
	
	private static Pattern taskLinePattern = Pattern.compile("(\"([^\"]*)\")|([^\\s]*)");

	private static ArrayList<String> parseTaskLine(String s) {
		Matcher matcher = taskLinePattern.matcher(s.replaceAll("\t", " "));
		ArrayList<String> tokens = new ArrayList<String>();
		while (matcher.find()) {
			String token = matcher.group(2);
			if (token != null && !"".equals(token)) {
				tokens.add(token);
				continue;
			}
			token = matcher.group(3);
			if (token != null && !"".equals(token)) {
				tokens.add(token);
			}
		}
		return tokens;
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
			return "Execute MIDL task";
		}
		
	}
}
