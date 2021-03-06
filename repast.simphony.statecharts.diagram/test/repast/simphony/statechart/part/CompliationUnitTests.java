package repast.simphony.statechart.part;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.junit.Test;

@SuppressWarnings("restriction")
public class CompliationUnitTests {

  private static final String PATH = "src/sample/";
  private static final String CONTENTS = "int a = 2;\na == 2;";
  private static final String BOOL_CLASS = "package sample; public class BooleanRetValCheck { public boolean method() {"
      + CONTENTS + "}}";

  class MemorySource extends SimpleJavaFileObject {
    private String src;

    public MemorySource(String name, String src) {
      super(URI.create("file:///" + name + ".java"), Kind.SOURCE);
      this.src = src;
    }

    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return src;
    }

    public OutputStream openOutputStream() {
      throw new IllegalStateException();
    }

    public InputStream openInputStream() {
      return new ByteArrayInputStream(src.getBytes());
    }
  }

  class MemoryByteCode extends SimpleJavaFileObject {
    private ByteArrayOutputStream baos;

    public MemoryByteCode(String name) {
      super(URI.create("byte:///" + name + ".class"), Kind.CLASS);
    }

    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      throw new IllegalStateException();
    }

    public OutputStream openOutputStream() {
      baos = new ByteArrayOutputStream();
      return baos;
    }

    public InputStream openInputStream() {
      throw new IllegalStateException();
    }

    public byte[] getBytes() {
      return baos.toByteArray();
    }
  }

  class SpecialJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    public SpecialJavaFileManager(StandardJavaFileManager sjfm) {
      super(sjfm);
    }

    public JavaFileObject getJavaFileForOutput(Location location, String name,
        JavaFileObject.Kind kind, FileObject sibling) throws IOException {
      MemoryByteCode mbc = new MemoryByteCode(name);
      return mbc;
    }
  }

  class PR implements IProblemRequestor {

    boolean error = false;

    @Override
    public void acceptProblem(IProblem problem) {
      error = true;
    }

    @Override
    public void beginReporting() {
      error = false;
    }

    @Override
    public void endReporting() {
    }

    @Override
    public boolean isActive() {
      return true;
    }
  }

  class Owner extends WorkingCopyOwner {
    PR pr = new PR();

    @Override
    public IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) {
      return pr;
    }
  }

  public IProject resetProject() throws CoreException {
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    IProject project = workspace.getRoot().getProject("test");
    if (!project.exists()) {
      project.create(null);
      project.open(null);

      IProjectDescription description = project.getDescription();
      description.setNatureIds(new String[] { JavaCore.NATURE_ID });
      project.setDescription(description, null);

      IFolder binFolder = project.getFolder("bin");
      binFolder.create(false, true, null);
      IJavaProject jp = JavaCore.create(project);
      jp.setOutputLocation(binFolder.getFullPath(), null);

      if (!project.getFolder("src").exists()) {
        project.getFolder("src").create(true, true, null);
      }

      List<IClasspathEntry> entries = new ArrayList<>();
      IVMInstall vmInstall = JavaRuntime.getDefaultVMInstall();
      LibraryLocation[] locations = JavaRuntime.getLibraryLocations(vmInstall);
      for (LibraryLocation element : locations) {
        entries.add(JavaCore.newLibraryEntry(element.getSystemLibraryPath(), null, null));
      }

      IClasspathEntry[] newEntries = new IClasspathEntry[entries.size()];
      entries.add(JavaCore.newSourceEntry(new Path("/test/src")));
      jp.setRawClasspath(entries.toArray(newEntries), null);

      IFolder folder = project.getFolder(PATH);

      if (!folder.exists()) {
        folder.create(true, true, null);
      }
    }

    return project;
  }

  @Test
  public void testCompilationProblem() throws CoreException {
    IProject project = resetProject();
    IJavaProject jp = JavaCore.create(project);
    IPackageFragment frag = jp.findPackageFragment(new Path("/test/src/sample"));
    System.out.println(frag);
    ICompilationUnit unit = frag.createCompilationUnit("BooleanRetValCheck.java", BOOL_CLASS,
        false, null);
    assertNotNull(unit);

    Owner owner = new Owner();
    ICompilationUnit je = unit.getWorkingCopy(owner, new NullProgressMonitor());
    je.reconcile(ICompilationUnit.NO_AST, false, owner, null);
    assertTrue(((PR) owner.getProblemRequestor(je)).error);

    IBuffer buffer = je.getBuffer();
    int pos = buffer.getContents().indexOf("a == 2");
    buffer.replace(pos, 0, "return ");

    je.reconcile(ICompilationUnit.NO_AST, false, owner, null);
    assertFalse(((PR) owner.getProblemRequestor(je)).error);
  }

  @Test
  public void testCompiler() {
    JavaCompiler javac = new EclipseCompiler();
    StandardJavaFileManager manager = javac.getStandardFileManager(null, null, null);
    SpecialJavaFileManager fileManager = new SpecialJavaFileManager(manager);
    List<MemorySource> compilationUnits = Arrays.asList(new MemorySource("BooleanRetValCheck", BOOL_CLASS));
    JavaCompiler.CompilationTask compile = javac.getTask(null, fileManager, null, null, null, compilationUnits);
    compile.call();
    
  }
}
