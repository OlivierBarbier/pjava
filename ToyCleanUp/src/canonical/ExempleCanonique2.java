package canonical;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.UndoEdit;

// https://help.eclipse.org/2019-12/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Freference%2Fapi%2Forg%2Feclipse%2Fjdt%2Fcore%2Fdom%2FAST.html
public class ExempleCanonique2 {

	public static void main(String[] args) throws Throwable, BadLocationException {
		String javaSourceCode = "import java.util.List;\nclass X {}\n";
		
		/* Crée un document qui contient du code source Java */
		Document document = new Document(javaSourceCode);
		
		/* Instancie un parser de code source Java */
		ASTParser parser = ASTParser.newParser(AST.JLS13);
		/* Indique au parseur le code source qu'il doit prendre en entrée */
		parser.setSource(document.get().toCharArray());
		parser.setResolveBindings(true);
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.recordModifications();
		AST ast = cu.getAST();
		
		/* Fabrique un noeud */
		ImportDeclaration id = ast.newImportDeclaration();
		id.setName(ast.newName(new String[] {"java", "util", "Set"}));
		/* Ajoute l'instruction d'import dans l'ASR*/
		cu.imports().add(id); 
		
		/**/
		TextEdit edits = cu.rewrite(document, null);
		UndoEdit undo = edits.apply(document);
		/**/
		
		System.out.println("Source java :");
		System.out.println(javaSourceCode);
		
		System.out.println("Source java après édition :");
		String afterEdit = document.get();
		System.out.println(afterEdit);
		
		System.out.println("Source java après édition et undo :");
		undo.apply(document);
		String afterUndo = document.get();
		System.out.println(afterUndo);
		
		Collection<String> C = new HashSet<>();
		Stream<String> strm = C.stream();
		
		Class<?> clazz = Class.forName("java.util.stream.Stream");
		System.out.println(clazz.isAssignableFrom(C.getClass()));
	
		System.out.println(document.get());
		
	}

}
