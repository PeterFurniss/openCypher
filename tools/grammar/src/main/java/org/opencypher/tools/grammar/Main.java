/*
 * Copyright (c) 2015-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencypher.tools.grammar;

import java.io.File;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiFunction;

import org.opencypher.grammar.Grammar;

import static java.lang.String.format;

import static org.opencypher.tools.Reflection.lambdaClass;
import static org.opencypher.tools.Reflection.pathOf;

/**
 * {@code Main-Class} for the jar, dispatches to the class (in this package) named (by {@linkplain Class#getSimpleName()
 * simple name}) in the first command line argument.
 *
 * Example: <code>java -jar grammar-tools.jar {@link ISO14977} cypher.xml</code>
 */
interface Main extends Serializable
{
    /**
     * Look up a named (first argument) class in this package, and invoke its main method.
     */
    static void main( String... args ) throws Throwable
    {
        if ( args == null || args.length < 1 )
        {
            String path = pathOf( Main.class );
            if ( new File( path ).isFile() && path.endsWith( ".jar" ) )
            {
                System.err.printf( "USAGE: java -jar %s <tool> ...%n", path );
            }
            else
            {
                System.err.printf( "USAGE: java -cp %s %s <tool> ...%n", path, Main.class.getName() );
            }
            System.exit( 1 );
        }
        else
        {
            Method main;
            try
            {
                Class<?> cls = Class.forName( Main.class.getPackage().getName() + '.' + args[0] );
                main = cls.getDeclaredMethod( "main", String[].class );
                if ( !Modifier.isStatic( main.getModifiers() ) || "Main".equals( args[0] ) )
                {
                    throw new IllegalArgumentException( args[0] );
                }
            }
            catch ( Exception e )
            {
                System.err.println( "Unknown formatter: " + args[0] );
                throw e;
            }
            try
            {
                main.invoke( null, (Object) Arrays.copyOfRange( args, 1, args.length ) );
            }
            catch ( InvocationTargetException e )
            {
                throw e.getTargetException();
            }
        }
    }

    void write( Grammar grammar, OutputStream out ) throws Exception;

    /**
     * Utility method for executing a program that operates on a grammar and produces output.
     *
     * Expects a single argument, the path to the grammar specification. If this is just the name of a file, it is
     * assumed to be a resource file within the jar, otherwise it is assumed to be a path on the file system.
     */
    static void execute( Main program, OutputStream out, String... args ) throws Exception
    {
        if ( args.length == 1 )
        {
            Grammar.ParserOption[] options = Grammar.ParserOption.from( System.getProperties() );
            Grammar grammar = null;
            String path = args[0];
            if ( path.indexOf( '/' ) == -1 )
            {
                URL resource = program.getClass().getResource( "/" + path );
                if ( resource != null )
                {
                    URI uri = resource.toURI();
                    try ( FileSystem ignored = FileSystems.newFileSystem( uri, Collections.emptyMap() ) )
                    {
                        grammar = Grammar.parseXML( Paths.get( uri ), options );
                    }
                }
            }
            if ( grammar == null )
            {
                grammar = Grammar.parseXML( Paths.get( path ), options );
            }
            program.write( grammar, out );
        }
        else
        {
            System.err.println( program.usage( ( cp, cls ) -> format(
                    "USAGE: java -cp %s %s <grammar.xml>%n", cp, cls ) ) );
            System.exit( 1 );
        }
    }

    static void execute( Main program, String... args ) throws Exception
    {
        execute( program, System.out, args );
    }

    default String usage( BiFunction<String, String, String> usage )
    {
        Class<?> implClass = lambdaClass( this );
        return usage.apply( pathOf( implClass ), implClass.getName() );
    }
}
