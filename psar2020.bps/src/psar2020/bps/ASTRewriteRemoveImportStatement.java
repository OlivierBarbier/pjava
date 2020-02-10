package psar2020.bps;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
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
				rewrite.remove(node, null);
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
	
	void rewriteAST(ASTRewrite rewrite, CompilationUnit unit)
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
