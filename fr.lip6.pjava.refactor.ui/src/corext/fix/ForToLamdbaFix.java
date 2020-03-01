package corext.fix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.text.edits.TextEdit;

public class ForToLamdbaFix implements ICleanUpFix {
	private static ICompilationUnit sourceDocument;
	private static CompilationUnit  compilationUnit;
	
	@Override
	public CompilationUnitChange createChange(IProgressMonitor progressMonitor) throws CoreException {
		final ASTRewrite rewriter = ASTRewrite.create(compilationUnit.getAST());
		
		/***************************************************************************/
		/*1 Sélectionne les "forEach" éligibles au réusinage ("refactoring")       */
		/***************************************************************************/
		// ?
		
		
		/***************************************************************/
		/*2 Effectue le refactoring sur l'AST au moyen de l'ASTRewrite */
		/***************************************************************/
		compilationUnit.accept(new ASTVisitor() {
			public void endVisit(EnhancedForStatement astFor) {
				
				try 
				{
					astFor.getBody().accept(new ASTVisitor() {
						@Override
						public void endVisit(SimpleName node) {
							IBinding b = node.resolveBinding();
							if (b instanceof org.eclipse.jdt.core.dom.IVariableBinding) {
								IVariableBinding c = (IVariableBinding)b;
								if ( ! c.getName().equals(astFor.getParameter().getName().getIdentifier()))
								{
									if (c.getModifiers() != org.eclipse.jdt.core.dom.Modifier.FINAL && ! c.isEffectivelyFinal()) {
										throw new RuntimeException("Neither final nor effectively final");
									}
								}
							}
						}

					});
				}
				catch (RuntimeException e)
				{
					return;
				}
				
				AST astFactory = astFor.getAST();
				
				// .stream()
				MethodInvocation astMethodInvocationStream = astFactory.newMethodInvocation();
				astMethodInvocationStream.setExpression((Expression)rewriter.createMoveTarget(astFor.getExpression()));
				astMethodInvocationStream.setName(astFactory.newSimpleName("stream"));
				
				// https://stackoverflow.com/questions/25834846/resolve-bindings-for-new-created-types
				
				// .forEach()
				MethodInvocation astMethodInvocationForEach = astFactory.newMethodInvocation();
				astMethodInvocationForEach.setExpression(astMethodInvocationStream);
				astMethodInvocationForEach.setName(astFactory.newSimpleName("forEach"));
				
				LambdaExpression astLambdaExpression = astFactory.newLambdaExpression();
				astLambdaExpression.parameters().add(rewriter.createMoveTarget(astFor.getParameter()));						
				
				
				if (astFor.getBody() instanceof Block)
				{
					astLambdaExpression.setBody(rewriter.createMoveTarget(astFor.getBody()));
				}
				else
				{
					Block astBlock = astFactory.newBlock();
					Statement astBody = astFor.getBody();
					astBlock.statements().add(rewriter.createMoveTarget(astBody));
					
					astLambdaExpression.setBody(astBlock);
				}
				
				astMethodInvocationForEach.arguments().add(astLambdaExpression);
				
				
				ExpressionStatement astExpressionStatement = astFactory.newExpressionStatement(astMethodInvocationForEach);
				rewriter.replace(astFor, astExpressionStatement, null);
			}
		});

		/*************************************************************************************************/
		/*3 Calcule les CompilationUnitChange (ASTRewrite -> TextEdit && CompilationUnitChange(TextEdit) */
		/*************************************************************************************************/
		CompilationUnitChange compilationUnitChange = computeChange(rewriter);
		
		return compilationUnitChange;
	}
	
	public static ICleanUpFix createCleanUp(CompilationUnit cu, boolean enabled) {
		// compilationUnit.recordModifications();
		sourceDocument = (ICompilationUnit)cu.getJavaElement();
		compilationUnit = cu;
		
		return new ForToLamdbaFix();
	}

	private static CompilationUnitChange computeChange(ASTRewrite rewriter)
	{
		CompilationUnitChange compilationUnitChange = new CompilationUnitChange("ForToLamdbaFix", sourceDocument);
		
		try {
			TextEdit textEdit = rewriter.rewriteAST();
			compilationUnitChange.setEdit(textEdit);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
	    return compilationUnitChange;	
	}
}
