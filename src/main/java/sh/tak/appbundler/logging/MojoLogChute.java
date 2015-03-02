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
package sh.tak.appbundler.logging;

import org.apache.maven.plugin.Mojo;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;

/**
 * Implementation of the Velocity's LogChute to be used in conjunction with a
 * Maven Mojo's logger.
 * 
 * @author John Vasquez
 */
public class MojoLogChute implements LogChute {

    private final Mojo mojo;
    
    /**
     * Creates the LogChute.
     * @param mojo The Maven Mojo 
     */
    public MojoLogChute(Mojo mojo) {
        this.mojo = mojo;
    }
    
    public void init(RuntimeServices rs) throws Exception {
        // do nothing
    }

    public void log(int i, String string) {
        switch(i) {
            case LogChute.TRACE_ID: mojo.getLog().debug(string); break;
            case LogChute.DEBUG_ID: mojo.getLog().debug(string); break;
            case LogChute.INFO_ID: mojo.getLog().info(string); break;
            case LogChute.WARN_ID: mojo.getLog().warn(string); break;
            case LogChute.ERROR_ID: mojo.getLog().error(string); break;
            default: mojo.getLog().debug(string);
        }
    }

    public void log(int i, String string, Throwable thrwbl) {
        switch(i) {
            case LogChute.TRACE_ID: mojo.getLog().debug(string, thrwbl); break;
            case LogChute.DEBUG_ID: mojo.getLog().debug(string, thrwbl); break;
            case LogChute.INFO_ID: mojo.getLog().info(string, thrwbl); break;
            case LogChute.WARN_ID: mojo.getLog().warn(string, thrwbl); break;
            case LogChute.ERROR_ID: mojo.getLog().error(string, thrwbl); break;
            default: mojo.getLog().debug(string, thrwbl);
        }
    }

    public boolean isLevelEnabled(int i) {
        switch(i) {
            case LogChute.TRACE_ID: return false;
            case LogChute.DEBUG_ID: return mojo.getLog().isDebugEnabled();
            case LogChute.INFO_ID: return mojo.getLog().isInfoEnabled();
            case LogChute.WARN_ID: return mojo.getLog().isWarnEnabled();
            case LogChute.ERROR_ID: return mojo.getLog().isErrorEnabled();
            default: return false;
        }
    }
    
}
