/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.cpd;

import java.util.Properties;

import org.antlr.v4.runtime.CharStream;

import net.sourceforge.pmd.cpd.token.AntlrToken;
import net.sourceforge.pmd.cpd.token.AntlrTokenFilter;
import net.sourceforge.pmd.lang.antlr.AntlrTokenManager;
import net.sourceforge.pmd.lang.lua.antlr4.LuaLexer;

/**
 * The Lua Tokenizer
 */
public class LuaTokenizer extends AntlrTokenizer {

    private boolean ignoreLiteralSequences = false;

    /**
     * Sets the possible options for the C# tokenizer.
     *
     * @param properties the properties
     * @see #OPTION_IGNORE_LITERAL_SEQUENCES
     */
    public void setProperties(Properties properties) {
        ignoreLiteralSequences = getBooleanProperty(properties, OPTION_IGNORE_LITERAL_SEQUENCES);
    }

    private boolean getBooleanProperty(final Properties properties, final String property) {
        return Boolean.parseBoolean(properties.getProperty(property, Boolean.FALSE.toString()));
    }

    @Override
    protected AntlrTokenManager getLexerForSource(SourceCode sourceCode) {
        CharStream charStream = AntlrTokenizer.getCharStreamFromSourceCode(sourceCode);
        return new AntlrTokenManager(new LuaLexer(charStream), sourceCode.getFileName());
    }

    @Override
    protected AntlrTokenFilter getTokenFilter(final AntlrTokenManager tokenManager) {
        return new LuaTokenFilter(tokenManager, ignoreLiteralSequences);
    }

    /**
     * The {@link LuaTokenFilter} extends the {@link AntlrTokenFilter} to discard
     * Lua-specific tokens.
     * <p>
     * By default, it enables annotation-based CPD suppression.
     * If the --ignoreUsings flag is provided, require() directives are filtered out.
     * </p>
     */
    private static class LuaTokenFilter extends AntlrTokenFilter {

        private final boolean ignoreLiteralSequences;
        private boolean discardingRequires = false;
        private boolean discardingNL = false;
        private AntlrToken discardingLiteralsUntil = null;
        private boolean discardCurrent = false;


        LuaTokenFilter(final AntlrTokenManager tokenManager, boolean ignoreLiteralSequences) {
            super(tokenManager);
            this.ignoreLiteralSequences = ignoreLiteralSequences;
        }

        @Override
        protected void analyzeToken(final AntlrToken currentToken) {
            skipNewLines(currentToken);
        }

        @Override
        protected void analyzeTokens(final AntlrToken currentToken, final Iterable<AntlrToken> remainingTokens) {
            discardCurrent = false;
            skipRequires(currentToken);
            skipLiteralSequences(currentToken, remainingTokens);
        }

        private void skipRequires(final AntlrToken currentToken) {
            final int type = currentToken.getKind();
            if (type == LuaLexer.REQUIRE) {
                discardingRequires = true;
            } else if (type == LuaLexer.CLOSE_PARENS && discardingRequires) {
                discardingRequires = false;
                discardCurrent = true;
            }
        }

        private void skipNewLines(final AntlrToken currentToken) {
            discardingNL = currentToken.getKind() == LuaLexer.NL;
        }

        private void skipLiteralSequences(final AntlrToken currentToken, final Iterable<AntlrToken> remainingTokens) {
            if (ignoreLiteralSequences) {
                final int type = currentToken.getKind();
                if (isDiscardingLiterals()) {
                    if (currentToken == discardingLiteralsUntil) { // NOPMD - intentional check for reference equality
                        discardingLiteralsUntil = null;
                        discardCurrent = true;
                    }
                } else if (type == LuaLexer.OPEN_BRACE
                    || type == LuaLexer.OPEN_BRACKET
                    || type == LuaLexer.OPEN_PARENS) {
                    final AntlrToken finalToken = findEndOfSequenceOfLiterals(remainingTokens);
                    discardingLiteralsUntil = finalToken;
                }
            }
        }

        private AntlrToken findEndOfSequenceOfLiterals(final Iterable<AntlrToken> remainingTokens) {
            boolean seenLiteral = false;
            int braceCount = 0;
            int bracketCount = 0;
            int parenCount = 0;
            for (final AntlrToken token : remainingTokens) {
                switch (token.getKind()) {
                case LuaLexer.INT:
                case LuaLexer.NORMAL_STRING:
                case LuaLexer.INTERPOLATED_STRING:
                case LuaLexer.LONG_STRING:
                case LuaLexer.HEX_FLOAT:
                case LuaLexer.HEX:
                case LuaLexer.FLOAT:
                case LuaLexer.NIL:
                case LuaLexer.BOOLEAN:
                    seenLiteral = true;
                    break; // can be skipped; continue to the next token
                case LuaLexer.COMMA:
                    break; // can be skipped; continue to the next token
                case LuaLexer.NL:
                    // this helps skip large multi-line data table sequences in Lua
                    break; // can be skipped; continue to the next token
                case LuaLexer.ASSIGNMENT:
                    // this helps skip large data table sequences in Lua: { ["bob"] = "uncle", ["alice"] = "enby" }
                    break; // can be skipped; continue to the next token
                case LuaLexer.OPEN_BRACE:
                    braceCount++;
                    break; // curly braces are allowed, as long as they're balanced
                case LuaLexer.CLOSE_BRACE:
                    braceCount--;
                    if (braceCount < 0) {
                        // end of the list in the braces; skip all contents
                        return seenLiteral ? token : null;
                    } else {
                        // curly braces are not yet balanced; continue to the next token
                        break;
                    }
                case LuaLexer.OPEN_BRACKET:
                    bracketCount++;
                    break; // brackets are allowed, as long as they're balanced
                case LuaLexer.CLOSE_BRACKET:
                    bracketCount--;
                    if (bracketCount < 0) {
                        // end of the list in the brackets; skip all contents
                        return seenLiteral ? token : null;
                    } else {
                        // brackets are not yet balanced; continue to the next token
                        break;
                    }
                case LuaLexer.OPEN_PARENS:
                    parenCount++;
                    break; // parens are allowed, as long as they're balanced
                case LuaLexer.CLOSE_PARENS:
                    parenCount--;
                    if (parenCount < 0) {
                        // end of the list in the parens; skip all contents
                        return seenLiteral ? token : null;
                    } else {
                        // parens are not yet balanced; continue to the next token
                        break;
                    }
                default:
                    // some other token than the expected ones; this is not a sequence of literals
                    return null;
                }
            }
            return null;
        }

        public boolean isDiscardingLiterals() {
            return discardingLiteralsUntil != null;
        }

        @Override
        protected boolean isLanguageSpecificDiscarding() {
            return discardingRequires || discardingNL || isDiscardingLiterals() || discardCurrent;
        }
    }
}
