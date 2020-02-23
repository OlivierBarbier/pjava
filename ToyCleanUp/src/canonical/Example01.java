package canonical;

import org.eclipse.jface.text.Document;

public class Example01 {

	public static void main(String[] args) {
		String javaSourceCode = 
				"import java.util.Set;\n" + 
				"class X {}\n";
	
		Document document = new Document(javaSourceCode);
	
		assert "import java.util.Set;\nclass X {}\n".equals(document.get());
		
		if (null instanceof Object) {
			;
		} else {
			System.out.println("pouet");
		}
	}

}
