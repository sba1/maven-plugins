package org.apache.maven.plugin.pmd;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Base class for mojos that check if there were any PMD violations.
 * 
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public abstract class AbstractPmdViolationCheckMojo
    extends AbstractMojo
{
    private final Boolean FAILURES_KEY = Boolean.TRUE;

    private final Boolean WARNINGS_KEY = Boolean.FALSE;

    /**
     * The location of the XML report to check, as generated by the PMD report.
     * 
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private File targetDirectory;

    /**
     * Whether to fail the build if the validation check fails.
     * 
     * @parameter expression="${pmd.failOnViolation}" default-value="true"
     * @required
     */
    private boolean failOnViolation;

    /**
     * The project language, for determining whether to run the report.
     * 
     * @parameter expression="${project.artifact.artifactHandler.language}"
     * @required
     * @readonly
     */
    private String language;

    /**
     * The project source directory.
     * 
     * @parameter expression="${project.build.sourceDirectory}"
     * @required
     * @readonly
     */
    private File sourceDirectory;

    /**
     * Print details of check failures to build output
     * 
     * @parameter expression="${pmd.verbose}" default-value="false"
     */
    private boolean verbose;

    protected void executeCheck( String filename, String tagName, String key, int failurePriority )
        throws MojoFailureException, MojoExecutionException
    {
        if ( "java".equals( language ) && sourceDirectory.exists() )
        {
            File outputFile = new File( targetDirectory, filename );
            if ( outputFile.exists() )
            {
                try
                {
                    XmlPullParser xpp = new MXParser();
                    FileReader freader = new FileReader( outputFile );
                    BufferedReader breader = new BufferedReader( freader );
                    xpp.setInput( breader );

                    Map violations = getViolations( xpp, tagName, failurePriority );

                    List failures = (List) violations.get( FAILURES_KEY );
                    List warnings = (List) violations.get( WARNINGS_KEY );

                    if ( verbose )
                    {
                        printErrors( failures, warnings );
                    }

                    int failureCount = failures.size();
                    int warningCount = warnings.size();

                    String message = getMessage( failureCount, warningCount, key, outputFile );

                    if ( failureCount > 0 && failOnViolation )
                    {
                        throw new MojoFailureException( message );
                    }

                    this.getLog().info( message );
                }
                catch ( IOException e )
                {
                    throw new MojoExecutionException(
                                                      "Unable to read PMD results xml: " + outputFile.getAbsolutePath(),
                                                      e );
                }
                catch ( XmlPullParserException e )
                {
                    throw new MojoExecutionException(
                                                      "Unable to read PMD results xml: " + outputFile.getAbsolutePath(),
                                                      e );
                }
            }
            else
            {
                throw new MojoFailureException( "Unable to perform check, " + "unable to find " + outputFile );
            }
        }
    }

    /**
     * Method for collecting the violations found by the PMD tool
     * 
     * @param xpp
     *            the xml parser object
     * @param tagName
     *            the element that will be checked
     * @return an int that specifies the number of violations found
     * @throws XmlPullParserException
     * @throws IOException
     */
    private Map getViolations( XmlPullParser xpp, String tagName, int failurePriority )
        throws XmlPullParserException, IOException
    {
        int eventType = xpp.getEventType();

        List failures = new ArrayList();
        List warnings = new ArrayList();

        while ( eventType != XmlPullParser.END_DOCUMENT )
        {
            if ( eventType == XmlPullParser.START_TAG && tagName.equals( xpp.getName() ) )
            {
                Map details = getErrorDetails( xpp );
                try
                {
                    int priority = Integer.parseInt( (String) details.get( "priority" ) );
                    if ( priority <= failurePriority )
                    {
                        failures.add( details );
                    }
                    else
                    {
                        warnings.add( details );
                    }
                }
                catch ( NumberFormatException e )
                {
                    // i don't know what priority this is. Treat it like a
                    // failure
                    failures.add( details );
                }
                catch ( NullPointerException e )
                {
                    // i don't know what priority this is. Treat it like a
                    // failure
                    failures.add( details );
                }

            }

            eventType = xpp.next();
        }

        HashMap map = new HashMap( 2 );
        map.put( FAILURES_KEY, failures );
        map.put( WARNINGS_KEY, warnings );
        return map;
    }

    /**
     * Prints the warnings and failures
     * 
     * @param failures
     *            list of failures
     * @param warnings
     *            list of warnings
     */
    protected void printErrors( List failures, List warnings )
    {
        Iterator iter = warnings.iterator();
        while ( iter.hasNext() )
        {
            printError( (Map) iter.next(), "Warning" );
        }

        iter = failures.iterator();
        while ( iter.hasNext() )
        {
            printError( (Map) iter.next(), "Failure" );
        }
    }

    /**
     * Gets the output message
     * 
     * @param failures
     * @param warnings
     * @param key
     * @param outputFile
     * @return
     */
    private String getMessage( int failureCount, int warningCount, String key, File outputFile )
    {
        StringBuffer message = new StringBuffer();
        if ( failureCount > 0 || warningCount > 0 )
        {
            if ( failureCount > 0 )
            {
                message.append( "You have " + failureCount + " " + key + ( failureCount > 1 ? "s" : "" ) );
            }

            if ( warningCount > 0 )
            {
                if ( failureCount > 0 )
                {
                    message.append( " and " );
                }
                else
                {
                    message.append( "You have " );
                }
                message.append( warningCount + " warning" + ( warningCount > 1 ? "s" : "" ) );
            }

            message.append( ". For more details see:" + outputFile.getAbsolutePath() );
        }
        return message.toString();
    }

    /**
     * Formats the failure details and prints them as an INFO message
     * 
     * @param item
     */
    abstract protected void printError( Map item, String severity );

    /**
     * Gets the attributes and text for the violation tag and puts them in a
     * HashMap
     * 
     * @param xpp
     * @throws XmlPullParserException
     * @throws IOException
     */
    abstract protected Map getErrorDetails( XmlPullParser xpp )
        throws XmlPullParserException, IOException;
}
