package corext.fix;

import java.util.Collection;
import java.util.HashSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
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
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.text.edits.TextEdit;

public class ForToLamdbaFix implements ICleanUpFix {
	private static ICompilationUnit sourceDocument;
	private static CompilationUnit  compilationUnit;
	private static ITypeBinding javaUtilCollectionTypeBinding;
	
	@Override
	public CompilationUnitChange createChange(IProgressMonitor progressMonitor) throws CoreException {
		final ASTRewrite rewriter = ASTRewrite.create(compilationUnit.getAST());
		final Collection<EnhancedForStatement> forStmts = new HashSet<>();
		
		/***************************************************************************/
		/*1 Sélectionne les "for" éligibles au refactoring                         */
		/***************************************************************************/
		compilationUnit.accept(new ASTVisitor() {
			@SuppressWarnings("unchecked")
			public void endVisit(EnhancedForStatement astFor) {
				ITypeBinding expression = astFor.getExpression().resolveTypeBinding();
				
				if (! expression.isSubTypeCompatible(javaUtilCollectionTypeBinding))
				{
					return;
				}
				
				try 
				{
					astFor.getBody().accept(new ASTVisitor() {
						@Override
						public void endVisit(SimpleName node) {
							IBinding nodeBinding = node.resolveBinding();
							if (node.resolveBinding() instanceof org.eclipse.jdt.core.dom.IVariableBinding) {
								IVariableBinding nodeVariablebinding = (IVariableBinding)nodeBinding;
								if ( ! nodeVariablebinding.getName().equals(astFor.getParameter().getName().getIdentifier()))
								{
									if (nodeVariablebinding.getModifiers() != org.eclipse.jdt.core.dom.Modifier.FINAL && ! nodeVariablebinding.isEffectivelyFinal()) {
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
				
				forStmts.add(astFor);
			}
		});
		
		/***************************************************************/
		/*2 Effectue le refactoring sur l'AST au moyen de l'ASTRewrite */
		/***************************************************************/
		forStmts.stream().forEach((EnhancedForStatement astFor) -> {	
			//for (<parameter>:<expression>)
			AST astFactory = astFor.getAST();
			
			// <expression>.stream()
			MethodInvocation astMethodInvocationStream = astFactory.newMethodInvocation();
			astMethodInvocationStream.setExpression((Expression)rewriter.createMoveTarget(astFor.getExpression()));
			astMethodInvocationStream.setName(astFactory.newSimpleName("stream"));
			
			// <expression>.stream().forEach()
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
			
			// <expression>.stream().forEach(lambda)
			astMethodInvocationForEach.arguments().add(astLambdaExpression);
			
			// pour ajouter un ;
			// <expression>.stream().forEach(lambda);
			ExpressionStatement astExpressionStatement = astFactory.newExpressionStatement(astMethodInvocationForEach);
			
			rewriter.replace(astFor, astExpressionStatement, null);
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
		
		try {
			// https://stackoverflow.com/questions/25834846/resolve-bindings-for-new-created-types
			// https://stackoverflow.com/questions/25916505/how-to-get-an-itypebinding-from-a-class-or-interface-name-string-with-eclipse			
			ASTParser parser = ASTParser.newParser(AST.JLS13);
			parser.setSource(compilationUnit.getJavaElement().getJavaProject().findType("java.util.Collection").getTypeRoot());
			parser.setResolveBindings(true);
			CompilationUnit node = (CompilationUnit)parser.createAST(null);
			javaUtilCollectionTypeBinding = ((TypeDeclaration) node.types().get(0)).resolveBinding();
		} catch (JavaModelException e1) {
			throw new RuntimeException("Cannot Parse java.util.Collection");
		}
		
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
