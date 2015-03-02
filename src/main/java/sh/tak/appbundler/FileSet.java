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

/**
 * Object of this class represents a set of file resources
 * that can be specified by directory and exclude/include patterns.
 * <p/>
 * Created date: Jan 19, 2008
 *
 * @author Zhenya Nyden
 */
public class FileSet extends org.apache.maven.model.FileSet {

    /**
     * When false, disables the default excludes.
     * Default value is true.
     *
     * @parameter expression="true"
     */
    private boolean useDefaultExcludes;

    /**
     * Getter for the useDefaultExcludes property.
     * Returns true if default excludes are going to be added to
     * the FileSet's list of excludes; false if only user excludes
     * are going to be used.
     *
     * @return Value for useDefaultExcludes property.
     */
    public boolean isUseDefaultExcludes() {
        return useDefaultExcludes;
    }

    /**
     * Setter for the useDefaultExcludes property.
     * Set it to false if default excludes should not be added to the FileSet's
     * list of excludes. Set it to true if default excludes are also required.
     *
     * @param useDefaultExcludes Value for the useDefaultExcludes to set.
     */
    public void setUseDefaultExcludes( boolean useDefaultExcludes ) {
        this.useDefaultExcludes = useDefaultExcludes;
    }

}
