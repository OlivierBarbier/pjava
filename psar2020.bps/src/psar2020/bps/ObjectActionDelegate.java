package psar2020.bps;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

public class ObjectActionDelegate implements IObjectActionDelegate {

	private ISelection selection;

	public void setActivePart(IAction action, IWorkbenchPart targetPart) {}

	public void run(IAction action) {
		if (selection instanceof IStructuredSelection) {
			ICompilationUnit lwUnit = (ICompilationUnit) ((IStructuredSelection) selection)
					.getFirstElement();
			createActionExuecutable(action.getId()).run(lwUnit);
		}

	}

	private ASTRewriteRemoveImportStatement createActionExuecutable(String id) {
		if ("psar2020.bps.app.objectContribution1.action1".equals(id)) {
			return new ASTRewriteRemoveImportStatement();
		}
		
		throw new IllegalArgumentException(id);
	}


	public void selectionChanged(IAction action, ISelection selection) {
		this.selection = selection;
	}
}
