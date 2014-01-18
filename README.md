# About

Maven plugin that creates an Application Bundle for OS X containing all your project dependencies and the necessary metadata.

As you may know, Apple has dropped Java development from OS X exculding security patches.
mojo's [osxappbundle-maven-plugin](http://mojo.codehaus.org/osxappbundle-maven-plugin/) depends on Apple's Java launcher, so it does not support Java version 7 and more (if installed).

Oracle's [Java Application Bundler](https://java.net/projects/appbundler) supports other Java runtime, but it does not support maven.

I merged both and fix to work maven plugin with latest Mac OS X.

# How to build

To build native launcher, run

```
sh build.sh
```

and install

```
mvn install
```

# How to use

A example configuration for pom.xml


```
<build>
    ...
    <plugins>
        ...
        <plugin>
            <groupId>io.github.appbundler</groupId>
            <artifactId>appbundle-maven-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
            <configuration>
                <mainClass>your.app.MainClass</mainClass>
            </configuration>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>bundle</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        ...
    </plugins>
    ...
</build>
```
