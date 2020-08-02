/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.internal;

import java.text.MessageFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.sourceforge.pmd.benchmark.TimeTracker;
import net.sourceforge.pmd.benchmark.TimedOperation;
import net.sourceforge.pmd.benchmark.TimedOperationCategory;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.InternalApiBridge;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.symbols.JClassSymbol;
import net.sourceforge.pmd.lang.java.symbols.JFieldSymbol;
import net.sourceforge.pmd.lang.java.symbols.JTypeDeclSymbol;
import net.sourceforge.pmd.lang.java.symbols.SymbolResolver;
import net.sourceforge.pmd.lang.java.symbols.internal.UnresolvedClassStore;
import net.sourceforge.pmd.lang.java.symbols.internal.ast.SymbolResolutionPass;
import net.sourceforge.pmd.lang.java.symbols.table.internal.SemanticChecksLogger;
import net.sourceforge.pmd.lang.java.symbols.table.internal.SymbolTableResolver;
import net.sourceforge.pmd.lang.java.types.JVariableSig.FieldSig;
import net.sourceforge.pmd.lang.java.types.TypeSystem;
import net.sourceforge.pmd.lang.java.types.internal.ast.LazyTypeResolver;
import net.sourceforge.pmd.lang.java.types.internal.infer.TypeInferenceLogger;
import net.sourceforge.pmd.lang.java.types.internal.infer.TypeInferenceLogger.SimpleLogger;
import net.sourceforge.pmd.lang.java.types.internal.infer.TypeInferenceLogger.VerboseLogger;

/**
 * Processes the output of the parser before rules get access to the AST.
 * This performs all semantic analyses in layered passes.
 */
public final class JavaAstProcessor {

    private static final Logger DEFAULT_LOG = Logger.getLogger(JavaAstProcessor.class.getName());

    private static final Map<ClassLoader, TypeSystem> TYPE_SYSTEMS = new IdentityHashMap<>();
    private static final Level INFERENCE_LOG_LEVEL;


    static {
        Level level;
        try {
            level = Level.parse(System.getenv("PMD_DEBUG_LEVEL"));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            level = Level.OFF;
        }
        INFERENCE_LOG_LEVEL = level;
    }


    private final TypeInferenceLogger typeInferenceLogger;
    private final SemanticChecksLogger logger;
    private final LanguageVersion languageVersion;
    private final TypeSystem typeSystem;

    private SymbolResolver symResolver;

    private final UnresolvedClassStore unresolvedTypes;


    private JavaAstProcessor(TypeSystem typeSystem,
                             SymbolResolver symResolver,
                             SemanticChecksLogger logger,
                             TypeInferenceLogger typeInfLogger,
                             LanguageVersion languageVersion) {

        this.symResolver = symResolver;
        this.logger = logger;
        this.typeInferenceLogger = typeInfLogger;
        this.languageVersion = languageVersion;

        this.typeSystem = typeSystem;
        unresolvedTypes = new UnresolvedClassStore(typeSystem.symbols());
    }

    static TypeInferenceLogger defaultTypeInfLogger() {
        if (INFERENCE_LOG_LEVEL == Level.FINEST) {
            return new VerboseLogger(System.err);
        } else if (INFERENCE_LOG_LEVEL == Level.FINE) {
            return new SimpleLogger(System.err);
        } else {
            return TypeInferenceLogger.noop();
        }
    }

    public JClassSymbol makeUnresolvedReference(String canonicalName) {
        return makeUnresolvedReference(canonicalName, 0);
    }

    public FieldSig makeUnresolvedField(@Nullable JClassSymbol enclosing, String simpleName) {
        JFieldSymbol sym = typeSystem.symbols().makeUnresolvedField(enclosing, simpleName);
        return typeSystem.sigOf(typeSystem.UNRESOLVED_TYPE, sym);
    }

    public JClassSymbol makeUnresolvedReference(JTypeDeclSymbol outer, String innerSimpleName) {
        if (outer instanceof JClassSymbol) {
            return makeUnresolvedReference(((JClassSymbol) outer).getCanonicalName() + '.' + innerSimpleName);
        }
        return makeUnresolvedReference(innerSimpleName); // child of a type variable does not exist
    }

    public JClassSymbol makeUnresolvedReference(String canonicalName, int typeArity) {
        return unresolvedTypes.makeUnresolvedReference(canonicalName, typeArity);
    }

    public SymbolResolver getSymResolver() {
        return symResolver;
    }

    public SemanticChecksLogger getLogger() {
        return logger;
    }

    public LanguageVersion getLanguageVersion() {
        return languageVersion;
    }

    public int getJdkVersion() {
        return ((JavaLanguageHandler) languageVersion.getLanguageVersionHandler()).getJdkVersion();
    }

    /**
     * Performs semantic analysis on the given source file.
     */
    public void process(ASTCompilationUnit acu) {
        /*
            PASSES:

            - qname resolution, setting symbols on declarations
              - now AST symbols are only partially initialized, their type-related methods will fail
            - symbol table resolution
              - AST symbols are now functional
            - AST disambiguation here
            - type resolution initialization
         */

        SymbolResolver knownSyms = bench("Symbol resolution", () -> SymbolResolutionPass.traverse(this, acu));

        // Now symbols are on the relevant nodes
        this.symResolver = SymbolResolver.layer(knownSyms, this.symResolver);

        // We set the type resolver before symbol table resolution
        // This is whacky, but there is a corner case with inner class creation expression (`foo.new Inner()`)
        // whereby the symbol to which `Inner` resolves depends on type resolution of `foo` (of the whole LHS).

        // This is handled in the disambiguation pass. Because the
        // SymbolTableResolver may request early disambiguation of some nodes,
        // type resolution must be ready to fire before table resolution starts
        LazyTypeResolver typeResolver = new LazyTypeResolver(this, typeInferenceLogger);
        InternalApiBridge.setTypeResolver(acu, typeResolver);

        bench("2. Symbol table resolution", () -> SymbolTableResolver.traverse(this, acu));
        bench("3. AST disambiguation", () -> InternalApiBridge.disambig(this, acu));
    }

    public TypeSystem getTypeSystem() {
        return typeSystem;
    }

    public static SemanticChecksLogger defaultLogger() {
        return new SemanticChecksLogger() {
            private String locPrefix(JavaNode loc) {
                return "[" + loc.getBeginLine() + "," + loc.getBeginColumn() + "] ";
            }

            @Override
            public void warning(JavaNode location, String message, Object... args) {
                DEFAULT_LOG.fine(() -> locPrefix(location) + MessageFormat.format(message, args));
            }

            @Override
            public void error(JavaNode location, String message, Object... args) {
                DEFAULT_LOG.severe(() -> locPrefix(location) + MessageFormat.format(message, args));
            }
        };
    }

    public static JavaAstProcessor create(SymbolResolver symResolver,
                                          TypeSystem typeSystem,
                                          LanguageVersion languageVersion,
                                          SemanticChecksLogger logger) {

        return new JavaAstProcessor(
            typeSystem,
            symResolver,
            logger,
            defaultTypeInfLogger(),
            languageVersion
        );
    }

    public static JavaAstProcessor create(ClassLoader classLoader,
                                          LanguageVersion languageVersion,
                                          SemanticChecksLogger logger,
                                          TypeInferenceLogger typeInfLogger) {

        TypeSystem typeSystem = TYPE_SYSTEMS.computeIfAbsent(classLoader, TypeSystem::new);
        return new JavaAstProcessor(
            typeSystem,
            typeSystem.bootstrapResolver(),
            logger,
            typeInfLogger,
            languageVersion
        );
    }

    public static void bench(String label, Runnable runnable) {
        try (TimedOperation to = TimeTracker.startOperation(TimedOperationCategory.LANGUAGE_SPECIFIC_PROCESSING, label)) {
            runnable.run();
        }
    }

    public static <T> T bench(String label, Supplier<T> runnable) {
        try (TimedOperation to = TimeTracker.startOperation(TimedOperationCategory.LANGUAGE_SPECIFIC_PROCESSING, label)) {
            return runnable.get();
        }
    }
}
