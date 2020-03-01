package canonical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.UndoEdit;


// class Clazz { public final java.util.stream.Stream s = Stream.of(1); }
// class Clazz { public static void main(String[] args) { java.util.stream.Stream s = java.util.stream.Stream.of(1); }}

// https://help.eclipse.org/2019-12/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fjdt%2Fcore%2Fdom%2FAST.html
public class ExempleCanonique {

	public static void main(String[] args) throws Throwable, BadLocationException {
		String statement = "class Clazz { public static void main(String[] args) { java.util.stream.Stream<java.lang.Integer> s = java.util.stream.Stream.of(1); }}\n";
		Document document = new Document(statement);
		ASTParser parser = ASTParser.newParser(AST.JLS13);
		parser.setSource(document.get().toCharArray());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		
		List<ITypeBinding> C = new ArrayList<>();
		CompilationUnit es = (CompilationUnit)parser.createAST(null);
		System.out.println(es.getAST().hasBindingsRecovery());
		
		es.accept(new ASTVisitor() {
			@Override
			public void endVisit(VariableDeclarationStatement node) {
				node.getType().resolveBinding().isAnnotation();
				System.out.println(1);
			}
		});
		
	}

}
