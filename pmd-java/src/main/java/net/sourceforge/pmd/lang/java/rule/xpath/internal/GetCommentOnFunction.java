/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.rule.xpath.internal;

import java.util.List;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.JavaComment;


/**
 * The XPath query "//VariableDeclarator[contains(getCommentOn(),
 * '//password')]" will find all variables declared that are annotated with the
 * password comment.
 *
 * @author Andy Throgmorton
 */
public class GetCommentOnFunction extends BaseJavaXPathFunction {


    public static final GetCommentOnFunction INSTANCE = new GetCommentOnFunction();

    protected GetCommentOnFunction() {
        super("getCommentOn");
    }

    @Override
    public Type getResultType() {
        return Type.OPTIONAL_STRING;
    }


    @Override
    public boolean dependsOnContext() {
        return true;
    }


    @Override
    public FunctionCall makeCallExpression() {
        return (contextNode, arguments) -> {
            int codeBeginLine = contextNode.getBeginLine();
            int codeEndLine = contextNode.getEndLine();

            List<JavaComment> commentList = contextNode.getFirstParentOfType(ASTCompilationUnit.class).getComments();
            for (JavaComment comment : commentList) {
                if (comment.getBeginLine() == codeBeginLine || comment.getEndLine() == codeEndLine) {
                    return comment.getText().toString();
                }
            }
            return null;
        };
    }
}



