package com.sca.sb.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import edu.umd.cs.findbugs.BugCollectionBugReporter;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugRanker;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.FindBugs2;
import edu.umd.cs.findbugs.HTMLBugReporter;
import edu.umd.cs.findbugs.Plugin;
import edu.umd.cs.findbugs.PluginException;
import edu.umd.cs.findbugs.Priorities;
import edu.umd.cs.findbugs.Project;
import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import edu.umd.cs.findbugs.charsets.UTF8;
import edu.umd.cs.findbugs.config.UserPreferences;
import edu.umd.cs.findbugs.plugins.DuplicatePluginIdException;
import edu.umd.cs.findbugs.test.AnalysisRunner;

@Service
public class SpotBugService {
	
	@Value("${analysis.dir.save}")
	private String filePath;
	
	@Value("${analysis.reports.filename}")
	private String htmlFileName;
	
	@Value("${analysis.reports.dir}")
	private String reportsDir;
	
	@Nullable
    private static final File PLUGIN_JAR;

    static {
        File jarFile;
        try {
            jarFile = createTempJar();
            Plugin.loadCustomPlugin(jarFile, null);
        } catch (DuplicatePluginIdException ignore) {
            // loading core plugin
            jarFile = null;
        } catch (IOException | URISyntaxException | PluginException e) {
            throw new AssertionError(e);
        }

        PLUGIN_JAR = jarFile;
    }
	
	public void analyzeFile(String classNameToAnalyze) throws Exception {
		
		Path path = Paths.get(filePath, classNameToAnalyze);
		System.out.println(classNameToAnalyze);
		run(path);
		
	}
	
	
	@Nonnull
    public void run(Path file) throws FileNotFoundException {
        DetectorFactoryCollection.resetInstance(new DetectorFactoryCollection());

        try (FindBugs2 engine = new FindBugs2(); Project project = createProject(file)) {
            engine.setProject(project);

            final DetectorFactoryCollection detectorFactoryCollection = DetectorFactoryCollection.instance();
            engine.setDetectorFactoryCollection(detectorFactoryCollection);

            BugCollectionBugReporter bugReporter = new BugCollectionBugReporter(project);
            bugReporter.setPriorityThreshold(Priorities.LOW_PRIORITY);
            bugReporter.setRankThreshold(BugRanker.VISIBLE_RANK_MAX);

            engine.setBugReporter(bugReporter);
            final UserPreferences preferences = UserPreferences.createDefaultUserPreferences();
            preferences.getFilterSettings().clearAllCategories();
            preferences.enableAllDetectors(true);
            engine.setUserPreferences(preferences);

            try {
                engine.execute();
            } catch (final IOException | InterruptedException e) {
                throw new AssertionError("Analysis failed with exception", e);
            }
            if (!bugReporter.getQueuedErrors().isEmpty()) {
                bugReporter.reportQueuedErrors();
                throw new AssertionError(
                        "Analysis failed with exception. Check stderr for detail.");
            }
            
            File reportFile= new File(reportsDir, htmlFileName);
            if (!reportFile.getParentFile().exists()){
            	 reportFile.getParentFile().mkdirs();
            }
            printHtml(reportFile, bugReporter, project);
            
        }
    }
	
	@CheckReturnValue
    private Project createProject(Path file) {
        final Project project = new Project();
        project.setProjectName(getClass().getSimpleName());
        if (PLUGIN_JAR != null) {
            try {
                String pluginId = Plugin.addCustomPlugin(PLUGIN_JAR.toURI()).getPluginId();
                project.setPluginStatusTrinary(pluginId, Boolean.TRUE);
            } catch (PluginException e) {
                throw new AssertionError("Failed to load plugin", e);
            }
        }

        if(file !=null) {
            project.addFile(file.toAbsolutePath().toString());
        }
        
        return project;
    }
	
	private static File createTempJar() throws IOException, URISyntaxException {
        ClassLoader cl = AnalysisRunner.class.getClassLoader();

        URL resource = cl.getResource("findbugs.xml");
        URI uri = resource.toURI();

        if ("jar".equals(uri.getScheme())) {
            JarURLConnection connection = (JarURLConnection) resource.openConnection();
            URL url = connection.getJarFileURL();
            return new File(url.toURI());
        }

        Path tempJar = File.createTempFile("SpotBugsAnalysisRunner", ".jar").toPath();
        try (OutputStream output = Files.newOutputStream(tempJar, StandardOpenOption.WRITE);
                JarOutputStream jar = new JarOutputStream(output)) {
            Path resourceRoot = Paths.get(uri).getParent();

            byte[] data = new byte[4 * 1024];
            Files.walkFileTree(resourceRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = resourceRoot.relativize(file).toString();
                    jar.putNextEntry(new ZipEntry(name));
                    try (InputStream input = Files.newInputStream(file, StandardOpenOption.READ)) {
                        int len;
                        while ((len = input.read(data)) > 0) {
                            jar.write(data, 0, len);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return tempJar.toFile();
    }
	
	private void printHtml(File f, BugCollectionBugReporter bugReporter, Project project) throws FileNotFoundException {
	          HTMLBugReporter reporter = new HTMLBugReporter(project, "default.xsl");
	          reporter.setIsRelaxed(true);
	          reporter.setOutputStream(UTF8.printStream(new FileOutputStream(f)));
	          for (BugInstance bug : bugReporter.getBugCollection().getCollection()) {
	            try {
	              if (true)
	                reporter.reportBug(bug); 
	            } catch (Exception e) {
	              e.printStackTrace();
	            } 
	          } 
	          reporter.finish();
	   
	  }
}
