package canonical;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jface.util.Util;

public class ASTTester {
 
	public static void main(String[] args) {
		String str = "public class ASTTester {"
				+ "public static void main(String[] args) {"
					+ "java.util.HashSet<java.lang.String> o;"
				+ "}"
				+ "}";
 
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setResolveBindings(true);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
 
		parser.setBindingsRecovery(true);
 
		Map options = JavaCore.getOptions();
		parser.setCompilerOptions(options);
 
		String unitName = "ASTTester.java";
		parser.setUnitName(unitName);
 
		String[] sources = { "/Users/olivierbarbier/eclipse-workspace-2019/ToyCleanUp/src" }; 
		String[] classpath = {"/Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/lib/rt.jar"};
 
		parser.setEnvironment(classpath, sources, new String[] { "UTF-8"}, true);
		parser.setSource(str.toCharArray());
 
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
 
		if (cu.getAST().hasBindingsRecovery()) {
			System.out.println("Binding activated.");
		}
 
		TypeFinderVisitor v = new TypeFinderVisitor();
		cu.accept(v);	
		VariableDeclarationExpression cvde = cu.getAST().newVariableDeclarationExpression(cu.getAST().newVariableDeclarationFragment());
		cvde.setType(cu.getAST().newSimpleType(cu.getAST().newName("java.util.stream.Stream")));
		VariableDeclarationFragment vdf = (VariableDeclarationFragment) cvde.fragments().get(0);
		System.out.println("binding: " + vdf.resolveBinding());
	}
}
 
class TypeFinderVisitor extends ASTVisitor{
 
	public boolean visit(VariableDeclarationStatement node){
		for (Iterator iter = node.fragments().iterator(); iter.hasNext();) {
			System.out.println("------------------");
 
			VariableDeclarationFragment fragment = (VariableDeclarationFragment) iter.next();
			IVariableBinding binding = fragment.resolveBinding();
 
			System.out.println("binding variable declaration: " +binding.getVariableDeclaration());
			System.out.println("binding: " +binding.getType().getInterfaces()[0]);
		}
		return true;
	}
}