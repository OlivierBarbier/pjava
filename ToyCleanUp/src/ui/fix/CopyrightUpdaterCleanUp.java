package ui.fix;

import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class CopyrightUpdaterCleanUp implements ICleanUp {
	private CleanUpOptions fOptions;
	private RefactoringStatus fStatus;
	
	@Override
	public void setOptions(CleanUpOptions options) {
		Assert.isLegal(options != null);
		Assert.isTrue(fOptions == null);
		fOptions = options;
	}

	@Override
	public String[] getStepDescriptions() {
		if (fOptions.isEnabled("cleanup.update_copyrights")) {
			return new String[] {"Update Copyrights"};
		}
		return null;
	}

	@Override
	public CleanUpRequirements getRequirements() {
		boolean changedRegionsRequired = false;
		Map compilerOptions = null;
		boolean isUpdateCopyrights = fOptions.isEnabled("cleanup.update_copyrights");
		
		return new CleanUpRequirements(
				isUpdateCopyrights,
				isUpdateCopyrights,
				changedRegionsRequired,
				compilerOptions
		);
	}
	
	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null)
			return null;

		return CopyrightsFix.createCleanUp(
				compilationUnit,
				fOptions.isEnabled("cleanup.update_copyrights")
		);
	}
	@Override
	public RefactoringStatus checkPreConditions(IJavaProject project, ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
		if (fOptions.isEnabled("cleanup.update_copyrights")) { //$NON-NLS-1$
			fStatus= new RefactoringStatus();
		}
		return new RefactoringStatus();
	}
	
	@Override
	public RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
		try {
			if (fStatus == null || fStatus.isOK()) {
				return new RefactoringStatus();
			} else {
				return fStatus;
			}
		} finally {
			fStatus= null;
		}
	}
}
