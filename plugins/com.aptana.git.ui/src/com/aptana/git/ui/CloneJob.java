package com.aptana.git.ui;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.ui.internal.ide.StatusUtil;
import org.eclipse.ui.statushandlers.StatusManager;

import com.aptana.git.core.model.GitExecutable;
import com.aptana.git.ui.internal.Launcher;
import com.aptana.git.ui.internal.sharing.ConnectProviderOperation;
import com.aptana.git.ui.internal.wizards.Messages;

//FIXME Move to some different package?
@SuppressWarnings("restriction")
public class CloneJob extends Job
{

	/**
	 * The name of the folder containing metadata information for the workspace.
	 */
	private static final String METADATA_FOLDER = ".metadata"; //$NON-NLS-1$

	private String sourceURI;
	private String dest;
	private boolean forceRootAsProject;

	public CloneJob(String sourceURI, String dest)
	{
		this(sourceURI, dest, false);
	}

	public CloneJob(String sourceURI, String dest, boolean forceRootAsProject)
	{
		super(Messages.CloneWizard_Job_title);
		setUser(true);
		this.sourceURI = sourceURI;
		this.dest = dest;
		this.forceRootAsProject = forceRootAsProject;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor)
	{
		SubMonitor subMonitor = SubMonitor.convert(monitor, 200);
		try
		{
			if (GitExecutable.instance() == null)
			{
				throw new CoreException(new Status(IStatus.ERROR, GitUIPlugin.getPluginId(),
						Messages.CloneJob_UnableToFindGitExecutableError));
			}
			ILaunch launch = Launcher.launch(GitExecutable.instance().path(), null, subMonitor.newChild(100),
					"clone", "--", sourceURI, dest); //$NON-NLS-1$ //$NON-NLS-2$
			if (launch == null)
			{
				throw new CoreException(new Status(IStatus.ERROR, GitUIPlugin.getPluginId(), MessageFormat.format(
						Messages.CloneJob_UnableToLaunchGitError, sourceURI, dest)));
			}
			while (!launch.isTerminated())
			{
				if (subMonitor.isCanceled())
					return Status.CANCEL_STATUS;
				Thread.yield();
			}
			Collection<File> existingProjects = new ArrayList<File>();
			if (!forceRootAsProject)
			{
				// Search the children of the repo for existing projects!
				existingProjects = collectProjectFilesFromDirectory(new File(dest), null, subMonitor.newChild(25));
			}
			if (existingProjects.isEmpty())
			{ // No projects found. Turn the root of the repo into a project!
				createExistingProject(new File(dest), subMonitor.newChild(75));
			}
			else
			{
				// TODO Should probably prompt user which projects to import
				int step = 75 / existingProjects.size();
				for (File file : existingProjects)
				{
					if (file == null)
						continue;
					createExistingProject(file, subMonitor.newChild(step));
				}
			}
		}
		catch (CoreException e)
		{
			GitUIPlugin.logError(e);
			return e.getStatus();
		}
		catch (Throwable e)
		{
			GitUIPlugin.logError(e.getMessage(), e);
			return new Status(IStatus.ERROR, GitUIPlugin.getPluginId(), e.getMessage(), e);
		}
		finally
		{
			subMonitor.done();
		}
		return Status.OK_STATUS;
	}

	/**
	 * Collect the list of .project files that are under directory into files.
	 * 
	 * @param directory
	 * @param directoriesVisited
	 *            Set of canonical paths of directories, used as recursion guard
	 * @param monitor
	 *            The monitor to report to
	 * @return boolean <code>true</code> if the operation was completed.
	 */
	private Collection<File> collectProjectFilesFromDirectory(File directory, Set<String> directoriesVisited,
			IProgressMonitor monitor)
	{

		if (monitor.isCanceled())
		{
			return Collections.emptyList();
		}
		// monitor.subTask(NLS.bind(
		// UIText.WizardProjectsImportPage_CheckingMessage, directory
		// .getPath()));
		File[] contents = directory.listFiles();
		if (contents == null)
			return Collections.emptyList();

		Collection<File> files = new HashSet<File>();

		// Initialize recursion guard for recursive symbolic links
		if (directoriesVisited == null)
		{
			directoriesVisited = new HashSet<String>();
			try
			{
				directoriesVisited.add(directory.getCanonicalPath());
			}
			catch (IOException exception)
			{
				GitUIPlugin.logError(exception.getMessage(), exception);
				StatusManager.getManager().handle(
						StatusUtil.newStatus(IStatus.ERROR, exception.getLocalizedMessage(), exception));
			}
		}

		// first look for project description files
		for (int i = 0; i < contents.length; i++)
		{
			File file = contents[i];
			if (file == null)
				continue;
			if (file.isFile() && file.getName().equals(IProjectDescription.DESCRIPTION_FILE_NAME))
			{
				files.add(file);
				// don't search sub-directories since we can't have nested
				// projects
				return files;
			}
		}
		// no project description found, so recurse into sub-directories
		for (int i = 0; i < contents.length; i++)
		{
			if (contents[i] == null)
				continue;
			if (contents[i].isDirectory())
			{
				if (!contents[i].getName().equals(METADATA_FOLDER))
				{
					try
					{
						String canonicalPath = contents[i].getCanonicalPath();
						if (!directoriesVisited.add(canonicalPath))
						{
							// already been here --> do not recurse
							continue;
						}
					}
					catch (IOException exception)
					{
						GitUIPlugin.logError(exception.getMessage(), exception);
						StatusManager.getManager().handle(
								StatusUtil.newStatus(IStatus.ERROR, exception.getLocalizedMessage(), exception));

					}
					files.addAll(collectProjectFilesFromDirectory(contents[i], directoriesVisited, monitor));
				}
			}
		}
		return files;
	}

	/**
	 * Create the project described in record. If it is successful return true.
	 * 
	 * @param record
	 * @param monitor
	 * @return boolean <code>true</code> if successful
	 * @throws CoreException
	 */
	private boolean createExistingProject(final File dest, IProgressMonitor monitor) throws CoreException
	{
		try
		{
			ProjectRecord record = new ProjectRecord(dest);
			String projectName = record.getProjectName();
			final IWorkspace workspace = ResourcesPlugin.getWorkspace();
			if (record.description == null)
			{
				// error case
				record.description = workspace.newProjectDescription(projectName);
				IPath locationPath = new Path(record.projectSystemFile.getAbsolutePath());

				// If it is under the root use the default location
				if (Platform.getLocation().isPrefixOf(locationPath))
				{
					record.description.setLocation(null);
				}
				else
				{
					record.description.setLocation(locationPath);
				}
			}
			else
			{
				record.description.setName(projectName);
			}

			IProject project = doCreateProject(record.description, new SubProgressMonitor(monitor, 80));
			if (project == null)
				return false;
			ConnectProviderOperation connectProviderOperation = new ConnectProviderOperation(project);
			connectProviderOperation.run(new SubProgressMonitor(monitor, 20));
		}
		finally
		{
			if (monitor != null)
				monitor.done();
		}
		return true;
	}

	protected IProject doCreateProject(final IProjectDescription desc, IProgressMonitor monitor) throws CoreException
	{
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final IProject project = workspace.getRoot().getProject(desc.getName());
		try
		{
			// monitor.beginTask(
			// UIText.WizardProjectsImportPage_CreateProjectsTask, 100);
			project.create(desc, new SubProgressMonitor(monitor, 30));
			project.open(IResource.BACKGROUND_REFRESH, new SubProgressMonitor(monitor, 50));
		}
		finally
		{
			monitor.done();
		}

		return project;
	}

	private class ProjectRecord
	{

		File projectSystemFile;
		String projectName;
		IProjectDescription description;

		/**
		 * Create a record for a project based on the info in the file.
		 * 
		 * @param file
		 */
		ProjectRecord(File file)
		{
			projectSystemFile = file;
			setProjectName();
		}

		/**
		 * Set the name of the project based on the projectFile.
		 */
		private void setProjectName()
		{
			try
			{
				// If we don't have the project name try again
				if (projectName == null)
				{
					IPath path = new Path(projectSystemFile.getPath());
					// if the file is in the default location, use the directory
					// name as the project name
					if (isDefaultLocation(path))
					{
						projectName = path.segment(path.segmentCount() - 2);
						description = ResourcesPlugin.getWorkspace().newProjectDescription(projectName);
					}
					else
					{
						description = ResourcesPlugin.getWorkspace().loadProjectDescription(path);
						projectName = description.getName();
					}

				}
			}
			catch (CoreException e)
			{
				// no good couldn't get the name
			}
		}

		/**
		 * Returns whether the given project description file path is in the default location for a project
		 * 
		 * @param path
		 *            The path to examine
		 * @return Whether the given path is the default location for a project
		 */
		private boolean isDefaultLocation(IPath path)
		{
			// The project description file must at least be within the project,
			// which is within the workspace location
			if (path.segmentCount() < 2)
				return false;
			return path.removeLastSegments(2).toFile().equals(Platform.getLocation().toFile());
		}

		/**
		 * Get the name of the project
		 * 
		 * @return String
		 */
		public String getProjectName()
		{
			return projectName;
		}
	}

}
