package io.github.appbundler.encoding;

/*
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

import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Default implementation of EncodingDetector. Reads the first characters of the input stream and uses a regular expression to find any
 * instances of <?xml encoding=".."?>
 */
public class DefaultEncodingDetector
    implements EncodingDetector
{

    private Pattern pattern = Pattern.compile( "<?(xml|XML).*encoding=\"(.*)\""); //.*encoding=\"(.*)\"" );

    private static final String DEFAULT_ENCODING = "UTF-8";

    public String detectXmlEncoding( InputStream inputStream )
    {
        Reader reader;

        try
        {
            reader = new InputStreamReader( inputStream, DEFAULT_ENCODING);
        }
        catch ( UnsupportedEncodingException e )
        {
            throw new IllegalStateException( "encoding not supported: " + DEFAULT_ENCODING);
        }

        char[] buffer = new char[1000];

        try
        {
            int read = reader.read( buffer );

            String string = new String( buffer, 0, read );
            Matcher matcher = pattern.matcher( string );
            if ( matcher.find() )
            {
                return matcher.group( 2 );
            }
            else
            {
                return DEFAULT_ENCODING;
            }
        }
        catch ( IOException e )
        {
            return DEFAULT_ENCODING;
        }

    }
}
