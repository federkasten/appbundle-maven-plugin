/*
 * Copyright 2014, Copyright 2014, Takashi AOKI, John Vasquez, Wolfgang Fahl, and other contributors. All rights reserved.
 * Copyright 2012, Oracle and/or its affiliates. All rights reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#import <Cocoa/Cocoa.h>
#include <dlfcn.h>
#include <jni.h>

#define JAVA_LAUNCH_ERROR "JavaLaunchError"

#define JVM_RUNTIME_KEY "JVMRuntime"
#define JVM_RUNTIME_PATH_KEY "JVMRuntimePath"
#define JVM_MAIN_CLASS_NAME_KEY "JVMMainClassName"
#define JVM_CLASS_PATHS_KEY "JVMClassPaths"
#define JVM_OPTIONS_KEY "JVMOptions"
#define JVM_ARGUMENTS_KEY "JVMArguments"
#define JVM_VERSION_KEY "JVMVersion"
#define JRE_PREFERRED_KEY "JREPreferred"
#define JDK_PREFERRED_KEY "JDKPreferred"
#define JVM_DEBUG_KEY "JVMDebug"
#define LAUNCHER_WORKING_DIRECTORY_KEY "LauncherWorkingDirectory"

#define UNSPECIFIED_ERROR "An unknown error occurred."

#define APP_ROOT_PREFIX "$APP_ROOT"

#define JVM_RUNTIME "$JVM_RUNTIME"

#define JAVA_RUNTIME  "/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home"
#define LIBJLI_DY_LIB "lib/jli/libjli.dylib"

//*
    #define DLog(...) NSLog(@"%s %@", __PRETTY_FUNCTION__, [NSString stringWithFormat:__VA_ARGS__])
/*/
    #define DLog(...) do { } while (0)
//*/

typedef int (JNICALL *JLI_Launch_t)(int argc, char ** argv,
                                    int jargc, const char** jargv,
                                    int appclassc, const char** appclassv,
                                    const char* fullversion,
                                    const char* dotversion,
                                    const char* pname,
                                    const char* lname,
                                    jboolean javaargs,
                                    jboolean cpwildcard,
                                    jboolean javaw,
                                    jint ergo);

char **jargv = NULL;
int jargc = 0;
bool firstTime = true;

int launch(char *);

NSString * findJavaDylib (NSString *, bool, bool, bool, bool);
NSString * findJREDylib (int, bool, bool);
NSString * findJDKDylib (int, bool, bool);
int extractMajorVersion (NSString *);
NSString * convertRelativeFilePath(NSString *);

int main(int argc, char *argv[]) {
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];

    int result;
    @try {
        // DRC: I have no idea why this program re-enters itself, but that's
        // why the "firstTime" check is necessary.
        if (argc > 1 && !jargv && firstTime) {
            jargv = &argv[1];
            jargc = argc - 1;
        }
        firstTime = false;
        launch(argv[0]);
        result = 0;
    } @catch (NSException *exception) {
        NSAlert *alert = [[NSAlert alloc] init];
        [alert setAlertStyle:NSCriticalAlertStyle];
        [alert setMessageText:[exception reason]];
        [alert runModal];

        result = 1;
    }

    [pool drain];

    return result;
}

int launch(char *commandName) {
    // Get the main bundle
    NSBundle *mainBundle = [NSBundle mainBundle];

    // Get the main bundle's info dictionary
    NSDictionary *infoDictionary = [mainBundle infoDictionary];

    // Test for debugging (but only on the second runthrough)
    bool isDebugging = [[infoDictionary objectForKey:@JVM_DEBUG_KEY] boolValue];

    if (isDebugging) {
        DLog(@"\n\n\n\nLoading Application '%@'", [infoDictionary objectForKey:@"CFBundleName"]);
    }

    // Set the working directory
    NSString *workingDirectory = [infoDictionary objectForKey:@LAUNCHER_WORKING_DIRECTORY_KEY];
    workingDirectory = [workingDirectory stringByReplacingOccurrencesOfString:@APP_ROOT_PREFIX withString:[mainBundle bundlePath]];
    chdir([workingDirectory UTF8String]);

    // Locate the JLI_Launch() function
    NSString *runtime = [infoDictionary objectForKey:@JVM_RUNTIME_KEY];
    NSString *runtimePath = [[mainBundle builtInPlugInsPath] stringByAppendingPathComponent:runtime];

    NSString *jvmRequired = [infoDictionary objectForKey:@JVM_VERSION_KEY];
    bool exactVersionMatch = ! ([jvmRequired hasSuffix:@"+"]);
    bool jrePreferred = [[infoDictionary objectForKey:@JRE_PREFERRED_KEY] boolValue];
    bool jdkPreferred = [[infoDictionary objectForKey:@JDK_PREFERRED_KEY] boolValue];

    if (jrePreferred && jdkPreferred) {
        DLog(@"Specifying both JRE- and JDK-preferred means neither is preferred");
        jrePreferred = false;
        jdkPreferred = false;
    }

    NSString *javaDylib;

    if (runtime != nil && [runtime length] > 0) {
      NSString *dylibRelPath = @"Contents/Home/jre";
      javaDylib = [[runtimePath stringByAppendingPathComponent:dylibRelPath] stringByAppendingPathComponent:@LIBJLI_DY_LIB];
      BOOL isDir;
      NSFileManager *fm = [[NSFileManager alloc] init];
      BOOL javaDylibFileExists = [fm fileExistsAtPath:javaDylib isDirectory:&isDir];
      if (!javaDylibFileExists || isDir) {
          dylibRelPath = @"Contents/Home";
          javaDylib = [[runtimePath stringByAppendingPathComponent:dylibRelPath] stringByAppendingPathComponent:@LIBJLI_DY_LIB];
          javaDylibFileExists = [fm fileExistsAtPath:javaDylib isDirectory:&isDir];
              if (!javaDylibFileExists || isDir) {
                  javaDylib = NULL;
              }
      }
    } else if (runtimePath != nil && [runtimePath length] > 0 ) {
      // Search for the runtimePath, then make it a libjli.dylib path.
      runtimePath = findJavaDylib (jvmRequired, jrePreferred, jdkPreferred, isDebugging, exactVersionMatch);
      javaDylib = [runtimePath stringByAppendingPathComponent:@LIBJLI_DY_LIB];

      if (isDebugging) {
          DLog(@"Java Runtime Dylib Path: '%@'", convertRelativeFilePath(javaDylib));
      }
    } else {
        javaDylib = @JAVA_RUNTIME;
    }

    const char *libjliPath = NULL;
    if (javaDylib != nil)
    {
        libjliPath = [javaDylib fileSystemRepresentation];
    }

    DLog(@"Launchpath: %s", libjliPath);

    void *libJLI = dlopen(libjliPath, RTLD_LAZY);

    JLI_Launch_t jli_LaunchFxnPtr = NULL;
    if (libJLI != NULL) {
        jli_LaunchFxnPtr = dlsym(libJLI, "JLI_Launch");
    }

    if (jli_LaunchFxnPtr == NULL) {
        [[NSException exceptionWithName:@JAVA_LAUNCH_ERROR
            reason:NSLocalizedString(@"JRELoadError", @UNSPECIFIED_ERROR)
            userInfo:nil] raise];
    }

    // Get the main class name
    NSString *mainClassName = [infoDictionary objectForKey:@JVM_MAIN_CLASS_NAME_KEY];
    if (mainClassName == nil) {
        [[NSException exceptionWithName:@JAVA_LAUNCH_ERROR
            reason:NSLocalizedString(@"MainClassNameRequired", @UNSPECIFIED_ERROR)
            userInfo:nil] raise];
    }

    // Set the class path
    NSString *mainBundlePath = [mainBundle bundlePath];
    NSString *javaPath = [mainBundlePath stringByAppendingString:@"/Contents/Java"];
    NSMutableString *classPath = [NSMutableString stringWithFormat:@"-Djava.class.path=%@/Classes", javaPath];

    NSFileManager *defaultFileManager = [NSFileManager defaultManager];
    NSArray *javaDirectoryContents = [defaultFileManager contentsOfDirectoryAtPath:javaPath error:nil];
    if (javaDirectoryContents == nil) {
        [[NSException exceptionWithName:@JAVA_LAUNCH_ERROR
            reason:NSLocalizedString(@"JavaDirectoryNotFound", @UNSPECIFIED_ERROR)
            userInfo:nil] raise];
    }

    for (NSString *file in javaDirectoryContents) {
        if ([file hasSuffix:@".jar"]) {
            [classPath appendFormat:@":%@/%@", javaPath, file];
        }
    }

    NSArray *classPathEntries = [infoDictionary objectForKey:@JVM_CLASS_PATHS_KEY];

    for (NSString *classPathEntry in classPathEntries) {
        [classPath appendFormat:@":%@/%@", javaPath, classPathEntry];
    }

    // Set the library path
    NSString *libraryPath = [NSString stringWithFormat:@"-Djava.library.path=%@/Contents/MacOS", mainBundlePath];

    // Get the VM options
    NSArray *options = [infoDictionary objectForKey:@JVM_OPTIONS_KEY];
    if (options == nil) {
        options = [NSArray array];
    }

    // Get the application arguments
    NSArray *arguments = [infoDictionary objectForKey:@JVM_ARGUMENTS_KEY];
    if (arguments == nil) {
        arguments = [NSArray array];
    }

    // Initialize the arguments to JLI_Launch()
    int argc = 1 + [options count] + 2 + [arguments count] + 1 + jargc;
    char *argv[argc];

    int i = 0;
    argv[i++] = commandName;
    argv[i++] = strdup([classPath UTF8String]);
    argv[i++] = strdup([libraryPath UTF8String]);

    for (NSString *option in options) {
        option = [option stringByReplacingOccurrencesOfString:@APP_ROOT_PREFIX withString:[mainBundle bundlePath]];
        argv[i++] = strdup([option UTF8String]);
    }

    argv[i++] = strdup([mainClassName UTF8String]);

    for (NSString *argument in arguments) {
        argument = [argument stringByReplacingOccurrencesOfString:@APP_ROOT_PREFIX withString:[mainBundle bundlePath]];
        argv[i++] = strdup([argument UTF8String]);
    }

    if (jargc > 0 && jargv) {
        int j;
        for (j = 0; j < jargc; j++) {
            if (!strncmp(jargv[j], "-psn", 4)) {
                argc--;
                continue;
            }
            argv[i++] = jargv[j];
        }
    }

    // Invoke JLI_Launch()
    return jli_LaunchFxnPtr(argc, argv,
                            0, NULL,
                            0, NULL,
                            "",
                            "",
                            "java",
                            "java",
                            FALSE,
                            FALSE,
                            FALSE,
                            0);
}


/**
 *  Searches for a JRE or JDK dylib of the specified version or later.
 *  First checks the "usual" JRE location, and failing that looks for a JDK.
 *  The version required should be a string of form "1.X". If no version is
 *  specified or the version is pre-1.7, then a Java 1.7 is sought.
 */
NSString * findJavaDylib (
        NSString *jvmRequired,
        bool jrePreferred,
        bool jdkPreferred,
        bool isDebugging,
        bool exactMatch)
{
    DLog (@"Searching for a JRE.");
    int required = extractMajorVersion(jvmRequired);

    if (required < 7)
    {
        if (isDebugging) { DLog (@"Required JVM must be at least ver. 7."); }
        required = 7;
    }

    if (isDebugging) {
        DLog (@"Searching for a Java %d", required);
    }

    //  First, if a JRE is acceptible, try to find one with required Java version.
    //  If found, return address for dylib that should be in the JRE package.
    if (jdkPreferred) {
        if (isDebugging) {
            DLog (@"A JDK is preferred; will not search for a JRE.");
        }
    }
    else {
        NSString * dylib = findJREDylib (required, isDebugging, exactMatch);

        if (dylib != nil) { return dylib; }

        if (isDebugging) { DLog (@"No matching JRE found."); }
    }

    // If JRE not found or if JDK preferred, look for an acceptable JDK
    // (probably in /Library/Java/JavaVirtualMachines if so). If found,
    // return return address of dylib in the JRE within the JDK.
    if (jrePreferred) {
        if (isDebugging) {
            DLog (@"A JRE is preferred; will not search for a JDK.");
        }
    }
    else {
        NSString * dylib = findJDKDylib (required, isDebugging, exactMatch);

        if (dylib != nil) { return dylib; }

        if (isDebugging) { DLog (@"No matching JDK found."); }
    }

    DLog (@"No matching JRE or JDK found.");

    return nil;
}

/**
 *  Searches for a JRE dylib of the specified version or later.
 */
NSString * findJREDylib (
        int jvmRequired,
        bool isDebugging,
        bool exactMatch)
{
    // Try the "java -version" shell command and see if we get a response and
    // if so whether the version  is acceptable.
    // If found, return address for dylib that should be in the JRE package.
    // Note that for unknown but ancient reasons, the result is output to stderr
    // rather than to stdout.
    @try
    {
        NSTask *task = [[NSTask alloc] init];
        [task setLaunchPath:[@JAVA_RUNTIME stringByAppendingPathComponent:@"bin/java"]];

        NSArray *args = [NSArray arrayWithObjects: @"-version", nil];
        [task setArguments:args];

        NSPipe *stdout = [NSPipe pipe];
        [task setStandardOutput:stdout];

        NSPipe *stderr = [NSPipe pipe];
        [task setStandardError:stderr];

        [task setStandardInput:[NSPipe pipe]];

        NSFileHandle *outHandle = [stdout fileHandleForReading];
        NSFileHandle *errHandle = [stderr fileHandleForReading];

        [task launch];
        [task waitUntilExit];
        [task release];

        NSData *data1 = [outHandle readDataToEndOfFile];
        NSData *data2 = [errHandle readDataToEndOfFile];

        NSString *outRead = [[NSString alloc] initWithData:data1
                                                  encoding:NSUTF8StringEncoding];
        NSString *errRead = [[NSString alloc] initWithData:data2
                                                  encoding:NSUTF8StringEncoding];

    //  Found something in errRead. Parse it for a Java version string and
    //  try to extract a major version number.
        if (errRead != nil) {
            int version = 0;

            // The result of the version command is 'java version "1.x"' or 'java version "9"'
            NSRange vrange = [errRead rangeOfString:@"java version \""];

            if (vrange.location != NSNotFound) {
                NSString *vstring = [errRead substringFromIndex:(vrange.location + 14)];

                vrange  = [vstring rangeOfString:@"\""];
                vstring = [vstring substringToIndex:vrange.location];

                version = extractMajorVersion(vstring);

                if (isDebugging) {
                    DLog (@"Found a Java %@ JRE", vstring);
                    DLog (@"Looks like major version %d", extractMajorVersion(vstring));
                }
            }

            if ( (version >= jvmRequired && !exactMatch) || (version == jvmRequired && exactMatch) ) {
                if (isDebugging) {
                    DLog (@"JRE version qualifies");
                }
                return @JAVA_RUNTIME;
            }
        }
    }
    @catch (NSException *exception)
    {
        DLog (@"JRE search exception: '%@'", [exception reason]);
    }

    return nil;
}

//  Having failed to find a JRE in the usual location, see if a JDK is installed
//  (probably in /Library/Java/JavaVirtualMachines). If so, return address of
//  dylib in the JRE within the JDK.
/**
 *  Searches for a JDK dylib of the specified version or later.
 */
NSString * findJDKDylib (
        int jvmRequired,
        bool isDebugging,
        bool exactMatch)
{
    @try
    {
        NSTask *task = [[NSTask alloc] init];
        [task setLaunchPath:@"/usr/libexec/java_home"];

        NSArray *args = [NSArray arrayWithObjects: @"-v", [NSString stringWithFormat:@"1.%i%@", jvmRequired, exactMatch?@"":@"+"], nil];
        [task setArguments:args];

        NSPipe *stdout = [NSPipe pipe];
        [task setStandardOutput:stdout];

        NSPipe *stderr = [NSPipe pipe];
        [task setStandardError:stderr];

        [task setStandardInput:[NSPipe pipe]];

        NSFileHandle *outHandle = [stdout fileHandleForReading];
        NSFileHandle *errHandle = [stderr fileHandleForReading];

        [task launch];
        [task waitUntilExit];
        [task release];

        NSData *data1 = [outHandle readDataToEndOfFile];
        NSData *data2 = [errHandle readDataToEndOfFile];

        NSString *outRead = [[NSString alloc] initWithData:data1
                                                    encoding:NSUTF8StringEncoding];
        NSString *errRead = [[NSString alloc] initWithData:data2
                                                    encoding:NSUTF8StringEncoding];

    //  If matching JDK not found, outRead will include something like
    //  "Unable to find any JVMs matching version "1.X"."
        if ( errRead != nil
                && [errRead rangeOfString:@"Unable"].location != NSNotFound )
        {
            if (isDebugging) {  DLog (@"No matching JDK found."); }
            return nil;
        }

        int version = 0;

        NSRange vrange = [outRead rangeOfString:@"jdk1."];
        if (vrange.location == NSNotFound) {
            // try the changed version layout from version 9
            vrange = [outRead rangeOfString:@"jdk-"];
            vrange.location += 4;
        } else {
            // otherwise remove the leading jdk
            vrange.location += 3;
        }

        if (vrange.location != NSNotFound) {
            NSString *vstring = [outRead substringFromIndex:(vrange.location)];

            vrange  = [vstring rangeOfString:@"/"];
            vstring = [vstring substringToIndex:vrange.location];

            version = extractMajorVersion(vstring);

            if (isDebugging) {
                DLog (@"Found a Java %@ JDK", vstring);
                DLog (@"Looks like major version %d", extractMajorVersion(vstring));
            }
        }

        if ( (version >= jvmRequired && !exactMatch) || (version == jvmRequired && exactMatch) ) {
            if (isDebugging) {
                DLog (@"JDK version qualifies");
            }
            return [[outRead stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]]
                                    stringByAppendingPathComponent:@"jre"];
        }
    }
    @catch (NSException *exception)
    {
        DLog (@"JDK search exception: '%@'", [exception reason]);
    }

    return nil;
}

/**
 *  Extract the Java major version number from a string. We expect the input
 *  to look like either either "1.X", "1.X.Y_ZZ" or "X.Y.ZZ", and the
 *  returned result will be the integral value of X. Any failure to parse the
 *  string will return 0.
 */
int extractMajorVersion (NSString *vstring)
{
    if (vstring == nil) { return 0; }

//  Expecting either a java version of form 1.X, 1.X.Y_ZZ or jdk1.X.Y_ZZ.
//  Strip off everything from start of req string up to and including the "1."
    NSUInteger vstart = [vstring rangeOfString:@"1."].location;

    if (vstart != NSNotFound) {
        // this is the version < 9 layout. Remove the leading 1.
        vstring = [vstring substringFromIndex:(vstart+2)];
    }

//  Now find the dot after the major version number, if present.
    NSUInteger vdot = [vstring rangeOfString:@"."].location;

//  No second dot, so return int of what we have.
    if (vdot == NSNotFound) {
        return [vstring intValue];
    }

//  Strip off everything beginning at that second dot.
    vstring = [vstring substringToIndex:vdot];

//  And convert what's left to an int.
    return [vstring intValue];
}

NSString * convertRelativeFilePath(NSString * path) {
    return [path stringByStandardizingPath];
}
