package psar2020.bps;

import java.util.Iterator;
import java.util.List;

import javax.lang.model.type.PrimitiveType;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ChildPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimplePropertyDescriptor;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.compiler.lookup.ParameterizedTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding;
import org.eclipse.jface.text.IDocument;


// cf. https://www.eclipse.org/articles/article.php?file=Article-JavaCodeManipulation_AST/index.html

public class ASTRewriteRemoveImportStatement {

	public void run(ICompilationUnit lwUnit) {
		CompilationUnit unit = parse(lwUnit);
		final ASTRewrite rewrite = ASTRewrite.create(unit.getAST());
		
		unit.accept(new ASTVisitor() {
			@Override
			public void endVisit(ImportDeclaration node) {
				super.endVisit(node);
				// Supprime le noeud "import" de l'AST
				// rewrite.remove(node, null);
			}
			
			
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
						
						tmp.getQualifiedName();
						
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

		rewriteAST(rewrite, unit);
	}
	
	protected CompilationUnit parse(ICompilationUnit lwUnit) {
		ASTParser parser = ASTParser.newParser(AST.JLS13);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(lwUnit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
	
	protected void rewriteAST(ASTRewrite rewrite, CompilationUnit unit)
	{
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		IPath path = unit.getJavaElement().getPath();
		try {
			bufferManager.connect(path, null, null);
			ITextFileBuffer textFileBuffer = bufferManager.getTextFileBuffer(path, null);
			IDocument document = textFileBuffer.getDocument();
			rewrite.rewriteAST(document, null).apply(document);
			textFileBuffer.commit(null, false);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				bufferManager.disconnect(path, null, null);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}		
	}
}
