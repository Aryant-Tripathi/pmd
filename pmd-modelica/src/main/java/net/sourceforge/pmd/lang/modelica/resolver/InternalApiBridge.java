/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.modelica.resolver;

import net.sourceforge.pmd.annotation.InternalApi;
import net.sourceforge.pmd.lang.modelica.ast.ASTStoredDefinition;
import net.sourceforge.pmd.lang.modelica.ast.ModelicaImportClause;
import net.sourceforge.pmd.lang.modelica.ast.Visibility;
import net.sourceforge.pmd.lang.modelica.resolver.internal.ResolutionContext;
import net.sourceforge.pmd.lang.modelica.resolver.internal.Watchdog;

@InternalApi
public final class InternalApiBridge {
    private InternalApiBridge() {}

    public static void addImportToClass(ModelicaClassType classTypeDeclaration, Visibility visibility, ModelicaImportClause clause) {
        ((ModelicaClassDeclaration) classTypeDeclaration).addImport(visibility, clause);
    }

    public static void addExtendToClass(ModelicaClassType classTypeDeclaration, Visibility visibility, CompositeName extendedClass) {
        ((ModelicaClassDeclaration) classTypeDeclaration).addExtends(visibility, extendedClass);
    }

    public static void resolveFurtherNameComponents(ModelicaDeclaration declaration, ResolutionContext result, CompositeName name) throws Watchdog.CountdownException {
        ((AbstractModelicaDeclaration) declaration).resolveFurtherNameComponents(result, name);
    }

    public static final class ModelicaSymbolFacade {
        private ModelicaSymbolFacade() {}

        public static void process(ASTStoredDefinition node) {
            ScopeAndDeclarationFinder sc = new ScopeAndDeclarationFinder();
            node.acceptVisitor(sc, null);
        }
    }
}
