# appbundler-plugin

## About

Maven plugin that creates an Application Bundle for OS X containing all your project dependencies and the necessary metadata.

As you may know, Apple has dropped Java development from OS X excluding security patches.
mojo's [osxappbundle-maven-plugin](http://mojo.codehaus.org/osxappbundle-maven-plugin/) depends on Apple's Java launcher, so it does not support Java version 7 and future.

Oracle's [Java Application Bundler](https://java.net/projects/appbundler) supports other Java runtime (including Java 7, 8 and more), but it does not support maven.

I merged both and fix to work as a maven plugin that supports latest Mac OS X.

## How to build

To build native application launcher, run

```shell
sh build.sh
```

and install

```shell
mvn install
```

## How to use

A example configuration for pom.xml is followings,

```xml
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

Package with following command,

```shell
mvn package appbundle:bundle
```

## How to create DMG

You can create DMG(Apple disk image) file with the following command,

```shell
hdiutil create -srcfolder path/to/archive path/to/YourApplication.dmg
```

## License

Copyright 2014, [Takashi AOKI][federkasten] and other contributors.

Copyright 2012, Oracle and/or its affiliates.

`native/main.m` is licensed under the [GNU General Public License version 2][gnu-general-public-license-2.0].

Other files are licensed under the [Apache License, Version 2.0][apache-license-2.0].

[federkasten]: http://federkasten.net
[gnu-general-public-license-2.0]: http://www.gnu.org/licenses/gpl-2.0.html
[apache-license-2.0]: http://www.apache.org/licenses/LICENSE-2.0.html
