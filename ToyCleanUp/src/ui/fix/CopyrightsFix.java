package ui.fix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;

import org.eclipse.core.filebuffers.FileBuffers;
import java.util.stream.Stream;

public class CopyrightsFix implements ICleanUpFix {

	private static ICompilationUnit sourceDocument;
	private static CompilationUnitChange cuc;

	@Override
	public CompilationUnitChange createChange(IProgressMonitor progressMonitor) throws CoreException {
        return cuc;
	}
	
	public static ICleanUpFix createCleanUp(CompilationUnit compilationUnit, boolean enabled) {
		compilationUnit.recordModifications();
		
		IJavaElement javaElement = compilationUnit.getJavaElement();
		
		if (javaElement.getElementType() != IJavaElement.COMPILATION_UNIT) {
			// Erreur
		}
		
		/*
		Le transtypage de IJavaElement en ICompilationUnit est sûr car
        javaElement.getElementType() == IJavaElement.COMPILATION_UNIT
		*/
		sourceDocument = (ICompilationUnit)javaElement;
		
		final ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
		
		Collection<MethodInvocation> forEachAsCollection = new HashSet<>();
		
		compilationUnit.accept(new ASTVisitor() {
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
				
				forEachAsCollection.add(dotForEach);
			}
		});
		
		// for ( FormalParameter : Expression ) Statement
		
		/*
		MethodInvocation:
		     [ Expression . ]
		         [ < Type { , Type } > ]
		         Identifier ( [ Expression { , Expression } ] )
		*/
		
		forEachAsCollection.stream()
			.filter(forEach -> {
				return forEach.arguments().size()==1 && forEach.arguments().get(0) instanceof LambdaExpression;
			})
			.filter(forEach -> {
				ITypeBinding dc = forEach.resolveMethodBinding().getDeclaringClass();
				return dc.isParameterizedType();
			})
			.filter(forEach -> {
				try {
					ITypeBinding dc = forEach.resolveMethodBinding().getDeclaringClass();
					Class<?> clazz = Class.forName("java.util.stream.Stream");
					Class<?> subClazz = Class.forName(dc.getErasure().getQualifiedName());
					return clazz.isAssignableFrom(subClazz);
				} catch (ClassNotFoundException e) {
					return false;
				}
			})
			.filter(forEach -> {
				/*
				 * stream =                +------+
				 *                         |      |
				 *              collection.stream().foreach(item -> { return item; });
				 */
				return forEach.getExpression() instanceof MethodInvocation;
			})
			.filter(forEach -> {
				/*
				 * stream =     +--------+        
				 *              |        |
				 *              collection.stream().foreach(item -> { return item; });
				 */
				return ((MethodInvocation)forEach.getExpression()).getExpression() instanceof org.eclipse.jdt.core.dom.Expression;
			})
			.forEach(forEach -> {
				AST ast = rewrite.getAST();
				
				/*
				 * stream =                +------+
				 *                         |      |
				 *              collection.stream().foreach(item -> { return item; });
				 */
				MethodInvocation stream = (MethodInvocation)forEach.getExpression();

				/*
				 * collection = +--------+
				 *              |        |
				 *              collection.stream().foreach(item -> { return item; });
				 */
				Expression collection = stream.getExpression();
				
				/*
				 * forEachLambda =             +----------------------+
				 *                             |                      |
				 * collection.stream().foreach(item -> { return item; });
				 */
				LambdaExpression forEachLambda = (LambdaExpression) forEach.arguments().get(0);
				
				/*
				 * forEachLambdaItem =         +--+
				 *                             |  |
				 * collection.stream().foreach(item -> { return item; });
				 */
				VariableDeclarationFragment forEachLambdaItem = (VariableDeclarationFragment)forEachLambda.parameters().get(0);
	
				/*
				 * forStmt =  +------------------------------------------+
				 *            |                                          |
				 *            for (<parameter> : <expression>) { <body>; }
				 */
				EnhancedForStatement forStmt = ast.newEnhancedForStatement();
				
				/*
				 * forStmtExpression =           +----------+
				 *                               |          |
				 *            for (<parameter> : <expression>) { <body>; }
				 */					
				Expression forStmtExpression = (Expression) rewrite.createMoveTarget(collection);
				forStmt.setExpression(forStmtExpression);

				/*
				 * forStmtParameter =       +---------+
				 *                          |         |
				 *                     for (<parameter> : <expression>) { <body>; }
				 */	
				SingleVariableDeclaration forStmtParameter = ast.newSingleVariableDeclaration();
				forStmtParameter.setName((SimpleName) rewrite.createMoveTarget(forEachLambdaItem.getName()));			
				//https://stackoverflow.com/questions/11091791/convert-eclipse-jdt-itypebinding-to-a-type
				String itemQualifiedName = forEachLambdaItem.resolveBinding().getType().getQualifiedName();
				Name itemName = ast.newName(itemQualifiedName);
				forStmtParameter.setType(ast.newSimpleType(itemName));
				forStmt.setParameter(forStmtParameter);
				
				/*
				 * forStmtBody =                                         +----+
				 *                                                       |    |
				 *                    for (<parameter> : <expression>) { <body>; }
				 */						
				forStmt.setBody((Statement) rewrite.createMoveTarget(forEachLambda.getBody()));
				
				
				rewrite.replace(forEach.getParent(), forStmt, null);				
			});
		;
		
		cuc = new CompilationUnitChange("CopyrightsFix", sourceDocument);
		TextEdit edt;
		try {
			edt = rewrite.rewriteAST();
			cuc.setEdit(edt);
			// cuc.addEdit(new InsertEdit(0, "/* GOOD BOY! */"));
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		/* Edit racine. Requis sinon runtime exception.*/
		
		// cuc.setEdit(new MultiTextEdit());
		
		//cuc.addEdit(new InsertEdit(0, "/* GOOD! */"));
		
		
		return new CopyrightsFix();
	}
	
	public static ICleanUpFix createCleanUp01(CompilationUnit compilationUnit, boolean enabled) {
		compilationUnit.recordModifications();
		
		IJavaElement javaElement = compilationUnit.getJavaElement();
		
		if (javaElement.getElementType() != IJavaElement.COMPILATION_UNIT) {
			// Erreur
		}
		
		/*
		Le transtypage de IJavaElement en ICompilationUnit est sûr car
        javaElement.getElementType() == IJavaElement.COMPILATION_UNIT
		*/
		sourceDocument = (ICompilationUnit)javaElement;
		
		final ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
		
		compilationUnit.accept(new ASTVisitor() {
			
			/* Je parcours tous les appels de méthode à la recherche
			 * de méthodes nommées "forEach".
			 * 
			 * Je vérifie que la méthode "forEach" est invoqué sur une instance
			 * qui implante l'interface "java.util.stream.Stream".
			 * 
			 * 
			 * Collection<String> collection = new HashSet<>();
			 * collection.stream().foreach(item -> { return item; });
			 */
			
			public void endVisit(MethodInvocation streamDotForEach) {
				try {
					super.endVisit(streamDotForEach);

					/*
					 * streamDotForEach =  +-------------------------------+
					 *                     |                               |
					 * collection.stream().foreach(item -> { return item; });
					 */
					if ( ! streamDotForEach.getName().getIdentifier().equals("forEach")) {
						return;
					}

					// Sur quel instance/class cette methode est-elle invoquée ?				
					ITypeBinding dc = streamDotForEach.resolveMethodBinding().getDeclaringClass();
					if ( ! dc.isParameterizedType()) {
						return;
					}
					
					Class<?> clazz;
					Class<?> subClazz;
					clazz = Class.forName("java.util.stream.Stream");
					subClazz = Class.forName(dc.getErasure().getQualifiedName());
					if ( ! clazz.isAssignableFrom(subClazz)) {
						return;
					}
					
					/*
					MethodInvocation:
	     				[ Expression . ]
	         			[ < Type { , Type } > ]
	         			Identifier ( [ Expression { , Expression } ] )
					*/
					Expression streamExpr = streamDotForEach.getExpression(); 
					if (streamExpr == null) {
						return;
					}
					if (!(streamExpr instanceof MethodInvocation)) {
						return;
					}
					
					AST ast = rewrite.getAST();
					
					/*
					 * stream =                +------+
					 *                         |      |
					 *              collection.stream().foreach(item -> { return item; });
					 */
					MethodInvocation stream = (MethodInvocation)streamExpr;

					/*
					 * collection = +--------+
					 *              |        |
					 *              collection.stream().foreach(item -> { return item; });
					 */
					Expression collection = stream.getExpression();
					
					/*
					 * forEachLambda =             +----------------------+
					 *                             |                      |
					 * collection.stream().foreach(item -> { return item; });
					 */
					LambdaExpression forEachLambda = (LambdaExpression) streamDotForEach.arguments().get(0);
					
					/*
					 * forEachLambdaItem =         +--+
					 *                             |  |
					 * collection.stream().foreach(item -> { return item; });
					 */
					VariableDeclarationFragment forEachLambdaItem = (VariableDeclarationFragment)forEachLambda.parameters().get(0);
		
					/*
					 * forStmt =  +------------------------------------------+
					 *            |                                          |
					 *            for (<parameter> : <expression>) { <body>; }
					 */
					EnhancedForStatement forStmt = ast.newEnhancedForStatement();
					
					/*
					 * forStmtExpression =           +----------+
					 *                               |          |
					 *            for (<parameter> : <expression>) { <body>; }
					 */					
					Expression forStmtExpression = (Expression) rewrite.createMoveTarget(collection);
					forStmt.setExpression(forStmtExpression);

					/*
					 * forStmtParameter =       +---------+
					 *                          |         |
					 *                     for (<parameter> : <expression>) { <body>; }
					 */	
					SingleVariableDeclaration forStmtParameter = ast.newSingleVariableDeclaration();
					forStmtParameter.setName((SimpleName) rewrite.createMoveTarget(forEachLambdaItem.getName()));			
					//https://stackoverflow.com/questions/11091791/convert-eclipse-jdt-itypebinding-to-a-type
					String itemQualifiedName = forEachLambdaItem.resolveBinding().getType().getQualifiedName();
					Name itemName = ast.newName(itemQualifiedName);
					forStmtParameter.setType(ast.newSimpleType(itemName));
					forStmt.setParameter(forStmtParameter);
					
					/*
					 * forStmtBody =                                         +----+
					 *                                                       |    |
					 *                    for (<parameter> : <expression>) { <body>; }
					 */						
					forStmt.setBody((Statement) rewrite.createMoveTarget(forEachLambda.getBody()));
					
					
					rewrite.replace(streamDotForEach, forStmt, null);
				
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
					return;
				}
			}
		});
		
		cuc = new CompilationUnitChange("CopyrightsFix", sourceDocument);
		TextEdit edt;
		try {
			edt = rewrite.rewriteAST();
			cuc.setEdit(edt);
			// cuc.addEdit(new InsertEdit(0, "/* GOOD BOY! */"));
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		/* Edit racine. Requis sinon runtime exception.*/
		
		// cuc.setEdit(new MultiTextEdit());
		
		//cuc.addEdit(new InsertEdit(0, "/* GOOD! */"));
		
		
		return new CopyrightsFix();
	}
	
	/* 1er essai 
	 * J'infère le type de c depuis C
	 * */
	public static ICleanUpFix createCleanUp00(CompilationUnit compilationUnit, boolean enabled) {
		compilationUnit.recordModifications();
		
		IJavaElement javaElement = compilationUnit.getJavaElement();
		
		if (javaElement.getElementType() != IJavaElement.COMPILATION_UNIT) {
			// Erreur
		}
		
		/*
		Le transtypage de IJavaElement en ICompilationUnit est sûr car
        javaElement.getElementType() == IJavaElement.COMPILATION_UNIT
		*/
		sourceDocument = (ICompilationUnit)javaElement;
		
		final ASTRewrite rewrite = ASTRewrite.create(compilationUnit.getAST());
		
		compilationUnit.accept(new ASTVisitor() {
			public void endVisit(MethodInvocation node) {
				super.endVisit(node);
				if(node.getName().getIdentifier().equals("forEach")) {
					MethodInvocation m = (MethodInvocation) node.getExpression();
					if (m.getName().getIdentifier().equals("stream")) {
						SimpleName C = (SimpleName)m.getExpression();
						IVariableBinding B = (IVariableBinding)C.resolveBinding();
						ITypeBinding PT = B.getType();
						
						/*java.lang.String*/
						ITypeBinding tmp = PT.getTypeArguments()[0];
						
						EnhancedForStatement forStmt = rewrite.getAST().newEnhancedForStatement();
						
						LambdaExpression firstArg = (LambdaExpression) node.arguments().get(0);
						VariableDeclarationFragment c = (VariableDeclarationFragment) firstArg.parameters().get(0);
						
						// for (..: C) (c le C)
						forStmt.setExpression((Expression) rewrite.createMoveTarget(C));

						
						SingleVariableDeclaration svd = rewrite.getAST().newSingleVariableDeclaration();
						svd.setName((SimpleName) rewrite.createMoveTarget(c.getName()));
						//svd.setType(rewrite.getAST().newPrimitiveType(org.eclipse.jdt.core.dom.PrimitiveType.BOOLEAN));
						
						//https://stackoverflow.com/questions/11091791/convert-eclipse-jdt-itypebinding-to-a-type
						svd.setType(rewrite.getAST().newSimpleType(rewrite.getAST().newName(tmp.getQualifiedName())));

						// for (? c : ..) (c le c)
						forStmt.setParameter(svd);
						
						forStmt.setBody((Statement) rewrite.createMoveTarget(firstArg.getBody()));
						rewrite.replace(node, forStmt, null);
					}
				}
			}
		});
		
		cuc = new CompilationUnitChange("CopyrightsFix", sourceDocument);
		TextEdit edt;
		try {
			edt = rewrite.rewriteAST();
			cuc.setEdit(edt);
			// cuc.addEdit(new InsertEdit(0, "/* GOOD BOY! */"));
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		/* Edit racine. Requis sinon runtime exception.*/
		
		// cuc.setEdit(new MultiTextEdit());
		
		//cuc.addEdit(new InsertEdit(0, "/* GOOD! */"));
		
		
		return new CopyrightsFix();
	}
}
