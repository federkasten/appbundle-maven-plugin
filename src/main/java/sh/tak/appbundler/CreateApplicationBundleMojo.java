/*
 * Copyright 2014, Takashi AOKI, John Vasquez, Wolfgang Fahl, and other contributors.
 * Copyright 2001-2008 The Codehaus.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sh.tak.appbundler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import org.apache.commons.lang.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.velocity.VelocityComponent;
import sh.tak.appbundler.logging.MojoLogChute;

/**
 * Package dependencies as an Application Bundle for Mac OS X.
 *
 * @goal bundle
 * @phase package
 * @requiresDependencyResolution runtime
 */
public class CreateApplicationBundleMojo extends AbstractMojo {

    /**
     * Default includes - everything is included.
     */
    private static final String[] DEFAULT_INCLUDES = {"**/**"};

    /**
     * The root where the generated classes are.
     */
    private static final String TARGET_CLASS_ROOT = "target" + File.separator + "classes";

    /**
     * Default JVM options passed to launcher
     */
    private static String[] defaultJvmOptions = {"-Dapple.laf.useScreenMenuBar=true"};

    /**
     * signals the Info.plit creator that a JRE is present.
     */
    private boolean embeddJre = false;

    /**
     * The Maven Project Object
     *
     * @parameter default-value="${project}"
     * @readonly
     */
    private MavenProject project;

    /**
     * The Maven Project Helper Object.
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    /**
     * The Velocity Component.
     *
     * @component
     * @readonly
     */
    private VelocityComponent velocity;

    /**
     * Paths to be put on the classpath in addition to the projects
     * dependencies. <br/><br/>
     * Might be useful to specify locations of dependencies in the provided
     * scope that are not distributed with the bundle but have a known location
     * on the system. <br/><br/>
     *
     * @see http://jira.codehaus.org/browse/MOJO-874
     * @parameter
     */
    private List<String> additionalClasspath;

    /**
     * Additional resources (as a list of <code>FileSet</code> objects) that
     * will be copied into the build directory and included in the .dmg
     * alongside with the application bundle.
     *
     * @parameter
     */
    private List<FileSet> additionalResources;

    /**
     * Additional files to bundle inside the Resources/Java directory and
     * include on the classpath. <br/><br/>
     * These could include additional JARs or JNI libraries.
     *
     * @parameter
     */
    private List<FileSet> additionalBundledClasspathResources;

    /**
     * The directory where the application bundle will be created.
     *
     * @parameter
     * default-value="${project.build.directory}/${project.build.finalName}";
     */
    private File buildDirectory;

    /**
     * The name of the Bundle. <br/><br/>
     * This is the name that is given to the application bundle; and it is also
     * what will show up in the application menu, dock etc.
     *
     * @parameter default-value="${project.name}"
     * @required
     */
    private String bundleName;

    /**
     * The location of the template for <code>Info.plist</code>. <br/><br/>
     *
     * Classpath is checked before the file system.
     *
     * @parameter default-value="sh/tak/appbundler/Info.plist.template"
     */
    private String dictionaryFile;

    /**
     * The location of the generated disk image (.dmg) file. <br/><br/>
     * This property depends on the <code>generateDiskImageFile</code> property.
     *
     * @parameter
     * default-value="${project.build.directory}/${project.build.finalName}.dmg"
     */
    private File diskImageFile;

    /**
     * If this is set to <code>true</code>, the generated disk image (.dmg) file
     * will be internet-enabled. <br/><br/>
     * The default is ${false}. This property depends on the
     * <code>generateDiskImageFile</code> property.
     *
     * This feature can only be executed in Mac OS X environments.
     *
     * @parameter default-value="false"
     */
    private boolean diskImageInternetEnable;

    /**
     * Tells whether to generate the disk image (.dmg) file or not. <br/><br/>
     * This feature can only be executed in Mac OS X and Linux environments.
     *
     * @parameter default-value="false"
     */
    private boolean generateDiskImageFile;

    /**
     * Tells whether to include a symbolic link to the generated disk image (.dmg) file or not. <br/><br/>
     * Relevant only if generateDiskImageFile is set.
     *
     * @parameter default-value="false"
     */
    private boolean includeApplicationsSymlink;


    /**
     * The icon (.icns) file for the bundle.
     *
     * @parameter
     */
    private String iconFile;

    /**
     * The name of the Java launcher, to execute when double-clicking the
     * Application Bundle.
     *
     * @parameter default-value="JavaAppLauncher";
     */
    private String javaLauncherName;

    /**
     * Options to the JVM, will be used as the value of <code>JVMOptions</code>
     * in the <code>Info.plist</code>.
     *
     * @parameter
     */
    private List<String> jvmOptions;

    /**
     * The value for the <code>JVMVersion</code> key.
     *
     * @parameter default-value="1.6+"
     */
    private String jvmVersion;

    /**
     * The main class to execute when double-clicking the Application Bundle.
     *
     * @parameter expression="${mainClass}"
     * @required
     */
    private String mainClass;

    /**
     * The version of the project. <br/><br/>
     * Will be used as the value of the <code>CFBundleVersion</code> key.
     *
     * @parameter default-value="${project.version}"
     */
    private String version;

    /**
     * The path to the working directory. <br/>
     * This can be inside or outside the app bundle. <br/>
     * To define a working directory <b>inside</b> the app bundle, use e.g.
     * <code>$APP_ROOT</code>.
     *
     * @parameter default-value="$APP_ROOT"
     */
    private String workingDirectory;

    /**
     * The path to the working directory. <br/>
     * This can be inside or outside the app bundle. <br/>
     * To define a working directory <b>inside</b> the app bundle, use e.g.
     * <code>$APP_ROOT</code>.
     *
     * @parameter default-value=""
     */
    private String jrePath;

    /**
     * The full path to the installation directory of the jre on the user's machine.
     *
     * @parameter default-value=""
     */
    private String jreFullPath;

    /**
     * Bundle project as a Mac OS X application bundle.
     *
     * @throws MojoExecutionException If an unexpected error occurs during
     * packaging of the bundle.
     */
    public void execute() throws MojoExecutionException {

        // 1. Create and set up directories
        getLog().info("Creating and setting up the bundle directories");
        buildDirectory.mkdirs();

        File bundleDir = new File(buildDirectory, bundleName + ".app");
        bundleDir.mkdirs();

        File contentsDir = new File(bundleDir, "Contents");
        contentsDir.mkdirs();

        File resourcesDir = new File(contentsDir, "Resources");
        resourcesDir.mkdirs();

        File javaDirectory = new File(contentsDir, "Java");
        javaDirectory.mkdirs();

        File macOSDirectory = new File(contentsDir, "MacOS");
        macOSDirectory.mkdirs();

        // 2. Copy in the native java application stub
        getLog().info("Copying the native Java Application Stub");
        File launcher = new File(macOSDirectory, javaLauncherName);
        launcher.setExecutable(true);

        FileOutputStream launcherStream = null;
        try {
            launcherStream = new FileOutputStream(launcher);
        } catch (FileNotFoundException ex) {
            throw new MojoExecutionException("Could not copy file to directory " + launcher, ex);
        }

        InputStream launcherResourceStream = this.getClass().getResourceAsStream(javaLauncherName);
        try {
            IOUtil.copy(launcherResourceStream, launcherStream);
        } catch (IOException ex) {
            throw new MojoExecutionException("Could not copy file " + javaLauncherName + " to directory " + macOSDirectory, ex);
        }

        // 3.Copy icon file to the bundle if specified
        if (iconFile != null) {
            File f = searchFile(iconFile, project.getBasedir());

            if (f != null && f.exists() && f.isFile()) {
                getLog().info("Copying the Icon File");
                try {
                    FileUtils.copyFileToDirectory(f, resourcesDir);
                } catch (IOException ex) {
                    throw new MojoExecutionException("Error copying file " + iconFile + " to " + resourcesDir, ex);
                }
            } else {
                throw new MojoExecutionException(String.format("Could not locate iconFile '%s'", iconFile));
            }
        }

        // 4. Resolve and copy in all dependencies from the pom
        getLog().info("Copying dependencies");
        List<String> files = copyDependencies(javaDirectory);
        if (additionalBundledClasspathResources != null && !additionalBundledClasspathResources.isEmpty()) {
            files.addAll(copyAdditionalBundledClasspathResources(javaDirectory, "lib", additionalBundledClasspathResources));
        }

        // 5. Check if JRE should be embedded. Check JRE path. Copy JRE
        if (jrePath != null) {
            File f = new File(jrePath);
            if (f.exists() && f.isDirectory()) {
                // Check if the source folder is a jdk-home
                File pluginsDirectory = new File(contentsDir, "PlugIns/JRE/Contents/Home/jre");
                pluginsDirectory.mkdirs();

                File sourceFolder = new File(jrePath, "Contents/Home");
                if (new File(jrePath, "Contents/Home/jre").exists()) {
                    sourceFolder = new File(jrePath, "Contents/Home/jre");
                }

                try {
                    getLog().info("Copying the JRE Folder from : [" + sourceFolder + "] to PlugIn folder: [" + pluginsDirectory + "]");
                    FileUtils.copyDirectoryStructure(sourceFolder, pluginsDirectory);
                    File binFolder = new File(pluginsDirectory, "bin");
                    //Setting execute permissions on executables in JRE
                    for (String filename : binFolder.list()) {
                        new File(binFolder, filename).setExecutable(true, false);
                    }

                    new File (pluginsDirectory, "lib/jspawnhelper").setExecutable(true,false);
                    embeddJre = true;
                } catch (IOException ex) {
                    throw new MojoExecutionException("Error copying folder " + f + " to " + pluginsDirectory, ex);
                }
            } else {
                getLog().warn("JRE not found check jrePath setting in pom.xml");
            }
        } else if (jreFullPath != null){
            getLog().info("JRE Full path is used [" + jreFullPath + "]");
            embeddJre = true;
        }

        // 6. Create and write the Info.plist file
        getLog().info("Writing the Info.plist file");
        File infoPlist = new File(bundleDir, "Contents" + File.separator + "Info.plist");
        this.writeInfoPlist(infoPlist, files);

        // 7. Copy specified additional resources into the top level directory
        getLog().info("Copying additional resources");
        if (additionalResources != null && !additionalResources.isEmpty()) {
            this.copyResources(buildDirectory, additionalResources);
        }

        // 7. Make the stub executable
        if (!SystemUtils.IS_OS_WINDOWS) {
            getLog().info("Making stub executable");
            Commandline chmod = new Commandline();
            try {
                chmod.setExecutable("chmod");
                chmod.createArgument().setValue("755");
                chmod.createArgument().setValue(launcher.getAbsolutePath());

                chmod.execute();
            } catch (CommandLineException e) {
                throw new MojoExecutionException("Error executing " + chmod + " ", e);
            }
        } else {
            getLog().warn("The stub was created without executable file permissions for UNIX systems");
        }

        // 8. Create the DMG file
        if (generateDiskImageFile) {
            if (SystemUtils.IS_OS_MAC_OSX) {
                getLog().info("Generating the Disk Image file");
                Commandline dmg = new Commandline();
                try {
                    // user wants /Applications symlink in the resulting disk image
                    if (includeApplicationsSymlink) {
                        createApplicationsSymlink();
                    }
                    dmg.setExecutable("hdiutil");
                    dmg.createArgument().setValue("create");
                    dmg.createArgument().setValue("-srcfolder");
                    dmg.createArgument().setValue(buildDirectory.getAbsolutePath());
                    dmg.createArgument().setValue(diskImageFile.getAbsolutePath());

                    try {
                        dmg.execute().waitFor();
                    } catch (InterruptedException ex) {
                        throw new MojoExecutionException("Thread was interrupted while creating DMG " + diskImageFile, ex);
                    } finally {
                        if (includeApplicationsSymlink) {
                            removeApplicationsSymlink();
                        }
                    }
                } catch (CommandLineException ex) {
                    throw new MojoExecutionException("Error creating disk image " + diskImageFile, ex);
                }

                if (diskImageInternetEnable) {
                    getLog().info("Enabling the Disk Image file for internet");
                    try {
                        Commandline internetEnableCommand = new Commandline();

                        internetEnableCommand.setExecutable("hdiutil");
                        internetEnableCommand.createArgument().setValue("internet-enable");
                        internetEnableCommand.createArgument().setValue("-yes");
                        internetEnableCommand.createArgument().setValue(diskImageFile.getAbsolutePath());

                        internetEnableCommand.execute();
                    } catch (CommandLineException ex) {
                        throw new MojoExecutionException("Error internet enabling disk image: " + diskImageFile, ex);
                    }
                }
                projectHelper.attachArtifact(project, "dmg", null, diskImageFile);
            }
            if (SystemUtils.IS_OS_LINUX) {
                getLog().info("Generating the Disk Image file");
                Commandline linux_dmg = new Commandline();
                try {
                    linux_dmg.setExecutable("genisoimage");
                    linux_dmg.createArgument().setValue("-V");
                    linux_dmg.createArgument().setValue(bundleName);
                    linux_dmg.createArgument().setValue("-D");
                    linux_dmg.createArgument().setValue("-R");
                    linux_dmg.createArgument().setValue("-apple");
                    linux_dmg.createArgument().setValue("-no-pad");
                    linux_dmg.createArgument().setValue("-o");
                    linux_dmg.createArgument().setValue(diskImageFile.getAbsolutePath());
                    linux_dmg.createArgument().setValue(buildDirectory.getAbsolutePath());

                    try {
                        linux_dmg.execute().waitFor();
                    } catch (InterruptedException ex) {
                        throw new MojoExecutionException("Thread was interrupted while creating DMG " + diskImageFile,
                                ex);
                    }
                } catch (CommandLineException ex) {
                    throw new MojoExecutionException("Error creating disk image " + diskImageFile + " genisoimage probably missing", ex);
                }
                projectHelper.attachArtifact(project, "dmg", null, diskImageFile);

            } else {
                getLog().warn("Disk Image file cannot be generated in non Mac OS X and Linux environments");
            }
        }

        getLog().info("App Bundle generation finished");
    }

    /**
     * The bundle name is used in paths, so we need to clean it for unwanted
     * characters, like ":" on MS Windows.
     *
     * @param bundleName the "unclean" bundle name.
     * @return a clean bundle name
     */
    private String cleanBundleName(String bundleName) {
        return bundleName.replace(':', '-');
    }

    /**
     * Copy all dependencies into the $JAVAROOT directory
     *
     * @param javaDirectory where to put jar files
     * @return A list of file names added
     * @throws MojoExecutionException
     */
    private List<String> copyDependencies(File javaDirectory) throws MojoExecutionException {
        ArtifactRepositoryLayout layout = new DefaultRepositoryLayout();

        List<String> list = new ArrayList<String>();

        // First, copy the project's own artifact
        File artifactFile = project.getArtifact().getFile();
        list.add(layout.pathOf(project.getArtifact()));

        try {
            FileUtils.copyFile(artifactFile, new File(javaDirectory, layout.pathOf(project.getArtifact())));
        } catch (IOException ex) {
            throw new MojoExecutionException("Could not copy artifact file " + artifactFile + " to " + javaDirectory, ex);
        }

        for (Artifact artifact : project.getArtifacts()) {
            File file = artifact.getFile();
            File dest = new File(javaDirectory, layout.pathOf(artifact));

            getLog().debug("Adding " + file);

            try {
                FileUtils.copyFile(file, dest);
            } catch (IOException ex) {
                throw new MojoExecutionException("Error copying file " + file + " into " + javaDirectory, ex);
            }

            list.add(layout.pathOf(artifact));
        }

        return list;
    }

    /**
     * Copy additional dependencies into the $JAVAROOT directory.
     *
     * @param javaDirectory
     * @param targetDirectoryName The directory within $JAVAROOT that these
     * resources will be copied to
     * @param additionalBundledClasspathResources
     * @return A list of file names added
     * @throws MojoExecutionException
     */
    private List<String> copyAdditionalBundledClasspathResources(File javaDirectory, String targetDirectoryName, List<FileSet> additionalBundledClasspathResources) throws MojoExecutionException {
        // Create the destination directory
        File destinationDirectory = new File(javaDirectory, targetDirectoryName);
        destinationDirectory.mkdirs();

        List<String> addedFilenames = this.copyResources(destinationDirectory, additionalBundledClasspathResources);

        return addPath(addedFilenames, targetDirectoryName);
    }

    /**
     * Modifies a String list of filenames to include an additional path.
     *
     * @param filenames
     * @param additionalPath
     * @return
     */
    private List<String> addPath(List<String> filenames, String additionalPath) {
        ArrayList<String> newFilenames = new ArrayList<String>(filenames.size());
        for (String filename : filenames) {
            newFilenames.add(additionalPath + '/' + filename);
        }

        return newFilenames;
    }

    /**
     * Writes an Info.plist file describing this bundle.
     *
     * @param infoPlist The file to write Info.plist contents to
     * @param files A list of file names of the jar files to add in $JAVAROOT
     * @throws MojoExecutionException
     */
    private void writeInfoPlist(File infoPlist, List<String> files) throws MojoExecutionException {
        Velocity.setProperty(Velocity.RUNTIME_LOG_LOGSYSTEM, new MojoLogChute(this));
        Velocity.setProperty("file.resource.loader.path", TARGET_CLASS_ROOT);

        try {
            Velocity.init();
        } catch (Exception ex) {
            throw new MojoExecutionException("Exception occured in initializing velocity", ex);
        }

        VelocityContext velocityContext = new VelocityContext();

        velocityContext.put("mainClass", mainClass);
        velocityContext.put("cfBundleExecutable", javaLauncherName);
        velocityContext.put("bundleName", cleanBundleName(bundleName));
        velocityContext.put("workingDirectory", workingDirectory);

        if (embeddJre && jrePath != null) {
            velocityContext.put("jrePath", "JRE");
            velocityContext.put("jreFullPath", "");
        } else if (embeddJre && jreFullPath != null) {
            velocityContext.put("jrePath", "");
            velocityContext.put("jreFullPath", jreFullPath);
        } else {
            velocityContext.put("jrePath", "");
            velocityContext.put("jreFullPath", "");
        }

        if (iconFile == null) {
            velocityContext.put("iconFile", "GenericJavaApp.icns");
        } else {
            File f = searchFile(iconFile, project.getBasedir());
            velocityContext.put("iconFile", (f != null && f.exists() && f.isFile()) ? f.getName() : "GenericJavaApp.icns");
        }

        velocityContext.put("version", version);
        velocityContext.put("jvmVersion", jvmVersion);

        StringBuilder options = new StringBuilder();
        options.append("<array>").append("\n      ");

        for (String jvmOption : defaultJvmOptions) {
            options.append("      ").append("<string>").append(jvmOption).append("</string>").append("\n");
        }

        options.append("      ").append("<string>").append("-Xdock:name=" + bundleName).append("</string>").append("\n");

        if (jvmOptions != null) {
            for (String jvmOption : jvmOptions) {
                options.append("      ").append("<string>").append(jvmOption).append("</string>").append("\n");
            }
        }

        options.append("    ").append("</array>");
        velocityContext.put("jvmOptions", options);

        StringBuilder jarFiles = new StringBuilder();
        jarFiles.append("<array>").append("\n");
        for (String file : files) {
            jarFiles.append("      ").append("<string>").append(file).append("</string>").append("\n");
        }

        if (additionalClasspath != null) {
            for (String pathElement : additionalClasspath) {
                jarFiles.append("      ").append("<string>").append(pathElement).append("</string>");
            }
        }
        jarFiles.append("    ").append("</array>");

        velocityContext.put("classpath", jarFiles.toString());
        try {
            File sourceInfoPlist = new File(TARGET_CLASS_ROOT, dictionaryFile);

            if (sourceInfoPlist.exists() && sourceInfoPlist.isFile()) {
                String encoding = detectEncoding(sourceInfoPlist);
                getLog().debug("Detected encoding " + encoding + " for dictionary file " + dictionaryFile);

                Writer writer = new OutputStreamWriter(new FileOutputStream(infoPlist), encoding);

                Template template = Velocity.getTemplate(dictionaryFile, encoding);
                template.merge(velocityContext, writer);

                writer.close();
            } else {
                Writer writer = new OutputStreamWriter(new FileOutputStream(infoPlist), "UTF-8");

                velocity.getEngine().mergeTemplate(dictionaryFile, "UTF-8", velocityContext, writer);

                writer.close();
            }
        } catch (IOException ex) {
            throw new MojoExecutionException("Could not write Info.plist to file " + infoPlist, ex);
        } catch (ParseErrorException ex) {
            throw new MojoExecutionException("Error parsing " + dictionaryFile, ex);
        } catch (ResourceNotFoundException ex) {
            throw new MojoExecutionException("Could not find resource for template " + dictionaryFile, ex);
        } catch (MethodInvocationException ex) {
            throw new MojoExecutionException("MethodInvocationException occured merging Info.plist template " + dictionaryFile, ex);
        } catch (Exception ex) {
            throw new MojoExecutionException("Exception occured merging Info.plist template " + dictionaryFile, ex);
        }
    }

    private static String detectEncoding(File file) throws Exception {
        return XMLInputFactory
                .newInstance()
                .createXMLStreamReader(new FileReader(file))
                .getCharacterEncodingScheme();
    }

    /**
     * Scan a fileset and get a list of files which it contains.
     *
     * @param fileset
     * @return list of files contained within a fileset.
     * @throws FileNotFoundException
     */
    private List<String> scanFileSet(File sourceDirectory, FileSet fileSet) {
        final String[] emptyStringArray = {};

        DirectoryScanner scanner = new DirectoryScanner();

        scanner.setBasedir(sourceDirectory);
        if (fileSet.getIncludes() != null && !fileSet.getIncludes().isEmpty()) {
            scanner.setIncludes(fileSet.getIncludes().toArray(emptyStringArray));
        } else {
            scanner.setIncludes(DEFAULT_INCLUDES);
        }

        if (fileSet.getExcludes() != null && !fileSet.getExcludes().isEmpty()) {
            scanner.setExcludes(fileSet.getExcludes().toArray(emptyStringArray));
        }

        if (fileSet.isUseDefaultExcludes()) {
            scanner.addDefaultExcludes();
        }

        scanner.scan();

        return Arrays.asList(scanner.getIncludedFiles());
    }

    /**
     * Copies given resources to the build directory.
     *
     * @param fileSets A list of FileSet objects that represent additional
     * resources to copy.
     * @throws MojoExecutionException In case of a resource copying error.
     */
    private List<String> copyResources(File targetDirectory, List<FileSet> fileSets) throws MojoExecutionException {
        ArrayList<String> addedFiles = new ArrayList<String>();
        for (FileSet fileSet : fileSets) {
            // Get the absolute base directory for the FileSet
            File sourceDirectory = new File(fileSet.getDirectory());

            if (!sourceDirectory.isAbsolute()) {
                sourceDirectory = new File(project.getBasedir(), sourceDirectory.getPath());
            }

            if (!sourceDirectory.exists()) {
                // If the requested directory does not exist, log it and carry on
                getLog().warn("Specified source directory " + sourceDirectory.getPath() + " does not exist.");
                continue;
            }

            List<String> includedFiles = scanFileSet(sourceDirectory, fileSet);
            addedFiles.addAll(includedFiles);

            getLog().info("Copying " + includedFiles.size() + " additional resource" + (includedFiles.size() > 1 ? "s" : ""));

            for (String destination : includedFiles) {
                File source = new File(sourceDirectory, destination);
                File destinationFile = new File(targetDirectory, destination);

                // Make sure that the directory we are copying into exists
                destinationFile.getParentFile().mkdirs();

                try {
                    FileUtils.copyFile(source, destinationFile);
                    destinationFile.setExecutable(fileSet.isExecutable(),false);

                } catch (IOException e) {
                    throw new MojoExecutionException("Error copying additional resource " + source, e);
                }
            }
        }
        return addedFiles;
    }

    private static File searchFile(String path, File basedir) {
        File f = new File(basedir, path);

        if (f.exists()) {
            return f;
        }

        f = new File(TARGET_CLASS_ROOT, path);

        if (f.exists()) {
            return f;
        }

        return null;
    }

    private void createApplicationsSymlink() throws MojoExecutionException, CommandLineException {
        Commandline symlink = new Commandline();
        symlink.setExecutable("ln");
        symlink.createArgument().setValue("-s");
        symlink.createArgument().setValue("/Applications");
        symlink.createArgument().setValue(buildDirectory.getAbsolutePath());
        try {
            symlink.execute().waitFor();
        } catch (InterruptedException ex) {
            throw new MojoExecutionException("Error preparing bundle disk image while creating symlink" + diskImageFile, ex);
        }
    }

    private void removeApplicationsSymlink() throws MojoExecutionException, CommandLineException {
        Commandline remSymlink = new Commandline();
        String symlink = buildDirectory.getAbsolutePath() + "/Applications";
        if (!new File(symlink).exists()) {
            return;
        }
        remSymlink.setExecutable("rm");
        remSymlink.createArgument().setValue(symlink);
        try {
            remSymlink.execute().waitFor();
        } catch (InterruptedException ex) {
            throw new MojoExecutionException("Error cleaning up (while removing " + symlink +
                    " symlink.) Please check permissions for that symlink" + diskImageFile, ex);
        }
    }
}
