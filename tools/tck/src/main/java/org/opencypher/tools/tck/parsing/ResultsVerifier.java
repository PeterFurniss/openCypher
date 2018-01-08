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
package org.opencypher.tools.tck.parsing;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.opencypher.tools.tck.parsing.generated.FeatureResultsBaseListener;

import java.util.BitSet;

/**
 * This class detects invalid formats of expected results values in feature files.
 */
class ResultsVerifier extends FeatureResultsBaseListener implements ANTLRErrorListener
{
    private boolean ok = true;

    boolean getOkAndReset()
    {
        boolean tmp = ok;
        ok = true;
        return tmp;
    }

    void notOk()
    {
        ok = false;
    }

    @Override
    public void syntaxError( Recognizer<?,?> recognizer, Object o, int i, int i1, String s, RecognitionException e )
    {
        notOk();
    }

    @Override
    public void reportAmbiguity( Parser parser, DFA dfa, int i, int i1, boolean b, BitSet bitSet,
            ATNConfigSet atnConfigSet )
    {
        notOk();
    }

    @Override
    public void reportAttemptingFullContext( Parser parser, DFA dfa, int i, int i1, BitSet bitSet,
            ATNConfigSet atnConfigSet )
    {
        notOk();
    }

    @Override
    public void reportContextSensitivity( Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atnConfigSet )
    {
        notOk();
    }
}
