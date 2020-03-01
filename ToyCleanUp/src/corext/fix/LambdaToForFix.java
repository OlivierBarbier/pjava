package corext.fix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.ui.text.correction.ASTResolving;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/* Grammaire JAVA
 * 
 * for ( FormalParameter : Expression ) Statement
 * 
 * 
 * MethodInvocation:
 *      [ Expression . ]
 *          [ < Type { , Type } > ]
 *          Identifier ( [ Expression { , Expression } ] )
 */
public class LambdaToForFix implements ICleanUpFix {

	private static ICompilationUnit sourceDocument;
	private static CompilationUnit  compilationUnit;
	
	@Override
	public CompilationUnitChange createChange(IProgressMonitor progressMonitor) throws CoreException {
		final ASTRewrite rewriter = ASTRewrite.create(compilationUnit.getAST());
	
		/***************************************************************************/
		/*1 Sélectionne les "forEach" éligibles au réusinage ("refactoring")       */
		/***************************************************************************/
		Stream<MethodInvocation> forEachAsStream = selectLambdaFor(compilationUnit);

		/*************************************************************/
		/*2 Effectue le réusinage sur l'AST au moyen de l'ASTRewrite */
		/*************************************************************/
		refactorLambdaForAsEnhancedFor(forEachAsStream, rewriter);

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
		
		return new LambdaToForFix();
	}
	
	private static CompilationUnitChange computeChange(ASTRewrite rewriter)
	{
		CompilationUnitChange compilationUnitChange = new CompilationUnitChange("CopyrightsFix", sourceDocument);
		
		try {
			TextEdit textEdit = rewriter.rewriteAST();
			compilationUnitChange.setEdit(textEdit);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
	    return compilationUnitChange;	
	}
	
	private static Stream<MethodInvocation> selectLambdaFor (CompilationUnit cu)
	{
		Collection<MethodInvocation> forEachAsCollection = new HashSet<>();
		
		cu.accept(new ASTVisitor() {			
			public void endVisit(MethodInvocation dotForEach) {
				/*
				 * dotForEach =        +---------------------------------------+
				 *                     |                                       |
				 *                     C.stream().foreach(c -> { c.equals(c); });
				 *                                |     |
				 * dotForEach.getName() =         +-----+
				 */
				if ( ! dotForEach.getName().getIdentifier().equals("forEach")) {
					return;
				}
				/*
				System.out.println(dotForEach.resolveMethodBinding().getDeclaringClass().getQualifiedName());
				ITypeBinding st = getStreamType();
				System.out.println(dotForEach.resolveMethodBinding().getDeclaringClass().isSubTypeCompatible(st));
				*/
				
				// dotForEach.resolveMethodBinding().overrides(ITypeBinding);
				// dotForEach.resolveMethodBinding().getDeclaringClass().isSubTypeCompatible(ITypeBinding)
				ITypeBinding declaringClass = dotForEach.resolveMethodBinding().getDeclaringClass();
				/*
				boolean found = false;
				for(ITypeBinding i : declaringClass.getInterfaces())
				{
					if (i.getErasure()!= null && i.getErasure().getQualifiedName().equals("java.util.stream.Stream")) {
						found = true;
						break;
					}
				}
				
				if (! found) {
					return;
				}
				*/
				forEachAsCollection.add(dotForEach);
			}
		});
		
		Stream<MethodInvocation> forEachAsStream = forEachAsCollection	
				.stream()
				.filter(forEach -> {
					return forEach.arguments().size()==1 && forEach.arguments().get(0) instanceof LambdaExpression;
				})
				.filter(forEach -> {
					/*
					 * stream =                +------+
					 *                         |      |
					 *              collection.stream().foreach(item -> { return item; });
					 */
					if ( ! (forEach.getExpression() instanceof MethodInvocation)) {
						return false;
					}

					MethodInvocation stream = (MethodInvocation)forEach.getExpression();
					
					/*
					 *              collection.stream().foreach(item -> { return item; });
					 *              |        |
					 * expression = +--------+
					 */
					Expression expression = stream.getExpression();

					return expression instanceof Expression;
				})
				.filter(forEach -> {
					/*
					 * On s'attend a ce que la classe sur laquelle est invoqué "forEach"
					 * implante l'interface "java.util.stream.Stream<T>".
					 * */
					try {
						/* Quel est la classe sur laquelle la méthode forEach est invoqué ? */
						ITypeBinding declaringClass = forEach.resolveMethodBinding().getDeclaringClass();

						
						if ( ! declaringClass.isParameterizedType()) {
							return false;
						}
						
						Class<?> expectedClass = Class.forName("java.util.stream.Stream");
						
						/* 
						 * dc.getErasure() = +---------------------+
						 *                   |                     |
	      				 *                   java.util.stream.Stream<T>
						 * */
						Class<?> actualClass = Class.forName(declaringClass.getErasure().getQualifiedName());
						
						/* Vérifie que la méthode "forEach" est invoquée depuis une expression
						 * qui s'évalue comme étant du type "java.util.stream.Stream<T>" */
						
						/*
						 * collection.stream().foreach(item -> { return item; });
						 *            |      |
						 *            +------+
						 *            implante java.util.stream.Stream ?
						 */
						return expectedClass.isAssignableFrom(actualClass);
					} catch (ClassNotFoundException e) {
						return false;
					}
				})
			;
		
		return forEachAsStream;
	}
	
	private static void refactorLambdaForAsEnhancedFor(Stream<MethodInvocation> forEachAsStream, ASTRewrite rewriter)
	{
		forEachAsStream.forEach(forEach -> {
			/******************/
			/* 2.1 Extraction */
			/******************/				
			/*
			 * forEachLambda =             +----------------------+
			 *                             |                      |
			 * collection.stream().foreach(item -> { return item; });
			 */
			LambdaExpression lambda = (LambdaExpression) forEach.arguments().get(0);
			/*
			 * forEachLambda =             +--+
			 *                             |  |
			 * collection.stream().foreach(item -> { return item; });
			 */				
			VariableDeclarationFragment item = (VariableDeclarationFragment)lambda.parameters().get(0);	
			/*
			 * collection = +--------+
			 *              |        |
			 *              collection.stream().foreach(item -> { return item; });
			 */
			Expression collection = ((MethodInvocation)forEach.getExpression()).getExpression();
			
			/****************/
			/* 2.2 Création */
			/****************/
			AST ast = rewriter.getAST();
			/*
			 * forStmt =  +------------------------------------------+
			 *            |                                          |
			 *            for (<parameter> : <expression>) { <body> }
			 */
			EnhancedForStatement forStmt = createEnhancedForStmt(
					ast, 
					
					/* parameter */
					createSingleVariableDeclaration(
							ast, 
							/* type */
							inferType(ast, item), 
							/* name */
							(SimpleName) rewriter.createMoveTarget(item.getName())
					),
					
					/* expression */
					(Expression) rewriter.createMoveTarget(collection), 
					
					/* body */
					(Statement)  rewriter.createMoveTarget(lambda.getBody())
			);			
			
			/*****************/
			/* 2.3 Réusinage */
			/*****************/
			rewriter.replace(forEach.getParent(), forStmt, null);				
		});
	}

	private static EnhancedForStatement createEnhancedForStmt(AST ast, SingleVariableDeclaration parameter, Expression collection, Statement body)
	{
		EnhancedForStatement forStmt = ast.newEnhancedForStatement();

		/*
		 * forStmtParameter =       +---------+
		 *                          |         |
		 *                     for (<parameter> : <expression>) { <body> }
		 */		
		forStmt.setParameter(parameter);		
		
		/* 
		 * forStmtExpression =           +----------+
		 *                               |          |
		 *            for (<parameter> : <expression>) { <body> }
		 */
		forStmt.setExpression(collection);

		/*
		 * forStmtBody =                                         +----+
		 *                                                       |    |
		 *                    for (<parameter> : <expression>) { <body> }
		 */						
		forStmt.setBody(body);
		
		
		return forStmt;
	}

	private static SingleVariableDeclaration createSingleVariableDeclaration(AST ast, Type itemType, SimpleName itemName)
	{
		/*
		 * forStmtParameter =       +---------+
		 *                          |         |
		 *                     for (<parameter> : <expression>) { <body>; }
		 */	
		SingleVariableDeclaration forStmtParameter = ast.newSingleVariableDeclaration();
		
		forStmtParameter.setName(itemName);			
		forStmtParameter.setType(itemType);
			
		return forStmtParameter;
	}
	
	/* https://stackoverflow.com/questions/11091791/convert-eclipse-jdt-itypebinding-to-a-type */
	private static Type inferType(AST ast, VariableDeclarationFragment variable)
	{
		return ast.newSimpleType(
			ast.newName(
					variable.resolveBinding().getType().getQualifiedName()
			)
		);		
	}
	
	private static Type getStreamType()
	{
		String statement = "java.util.stream.Stream<java.lang.String> s;\n";
		Document document = new Document(statement);
		ASTParser parser = ASTParser.newParser(AST.JLS13);
		parser.setSource(document.get().toCharArray());
		parser.setResolveBindings(true);
		parser.setKind(ASTParser.K_STATEMENTS);
		List<Type> C = new ArrayList<>();
		Block es = (Block)parser.createAST(null);
		es.accept(new ASTVisitor() {
			public void endVisit(VariableDeclarationStatement vd)
			{
				C.add(vd.getType());
			}
		});
		return C.get(0);		
	}
}
