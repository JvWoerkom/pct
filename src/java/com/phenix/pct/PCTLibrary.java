/*
 * Copyright  2000-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.phenix.pct;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.types.FileSet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Class for managing Progress library files
 * 
 * @author <a href="mailto:g.querret+PCT@gmail.com">Gilles QUERRET </a>
 */
public class PCTLibrary extends PCT {
    private boolean deleteTempFile = false;
    private int tmpFileID = -1;
    private int tmpLibraryID = -1;
    private File tmpFile = null;
    private File destFile = null;
    private String encoding = null;
    private String cpInternal = null;
    private String cpStream = null;
    private String cpColl = null;
    private String cpCase = null;
    private boolean noCompress = false;
    private boolean debugPCT = false;
    private FileSet fileset = new FileSet();
    private List<FileSet> filesets = new ArrayList<FileSet>();
    private File baseDir = null;
    private File sharedFile = null;

    // Files containing at least one space in file name : these files are handled separately (prolib
    // bug). Key is baseDir, value is the file list
    private Map<File, List<String>> spaceFiles = new HashMap<File, List<String>>();

    /**
     * Default constructor
     * 
     */
    public PCTLibrary() {
        super();
        tmpFileID = nextRandomInt();
        tmpLibraryID = nextRandomInt(); 
        tmpFile = new File(System.getProperty("java.io.tmpdir"), "PCTLib" + tmpFileID + ".txt"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * Shared library name
     * 
     * @param sharedName File name
     * @since 0.14
     */
    public void setSharedFile(File sharedName) {
        this.sharedFile = sharedName;
    }

    /**
     * Adds a set of files to archive.
     * 
     * @param set FileSet
     */
    public void addFileset(FileSet set) {
        filesets.add(set);
    }

    /**
     * Library file name to create/update
     * 
     * @param destFile File
     */
    public void setDestFile(File destFile) {
        this.destFile = destFile;
    }

    /**
     * Codepage to use
     * 
     * @param encoding String
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Internal codepage to use
     * 
     * @param cpInternal String
     */
    public void setCpInternal(String cpInternal) {
        this.cpInternal = cpInternal;
    }

    /**
     * Stream codepage to use
     * 
     * @param cpStream String
     */
    public void setCpStream(String cpStream) {
        this.cpStream = cpStream;
    }

    /**
     * Collation table to use
     * 
     * @param cpColl String
     */
    public void setCpColl(String cpColl) {
        this.cpColl = cpColl;
    }

    /**
     * Case table to use
     * 
     * @param cpColl String
     */
    public void setCpCase(String cpCase) {
        this.cpCase = cpCase;
    }

    /**
     * Compress library at the end of the process
     * 
     * @param noCompress boolean
     */
    public void setNoCompress(boolean noCompress) {
        this.noCompress = noCompress;
    }

    /**
     * Directory from which to archive files; optional.
     * 
     * @param baseDir File
     */
    public void setBasedir(File baseDir) {
        this.baseDir = baseDir;
        this.fileset.setProject(getProject());
        this.fileset.setDir(baseDir);
    }

    /**
     * Sets the set of include patterns. Patterns may be separated by a comma or a space.
     * 
     * @param includes the string containing the include patterns
     */
    public void setIncludes(String includes) {
        fileset.setIncludes(includes);
    }

    /**
     * Sets the set of exclude patterns. Patterns may be separated by a comma or a space.
     * 
     * @param excludes the string containing the exclude patterns
     */
    public void setExcludes(String excludes) {
        fileset.setExcludes(excludes);
    }

    /**
     * Sets the name of the file containing the includes patterns.
     * 
     * @param includesfile A string containing the filename to fetch the include patterns from.
     */
    public void setIncludesfile(File includesfile) {
        fileset.setIncludesfile(includesfile);
    }

    /**
     * Sets the name of the file containing the includes patterns.
     * 
     * @param excludesfile A string containing the filename to fetch the include patterns from.
     */
    public void setExcludesfile(File excludesfile) {
        fileset.setExcludesfile(excludesfile);
    }

    /**
     * Sets whether default exclusions should be used or not.
     * 
     * @param useDefaultExcludes "true"|"on"|"yes" when default exclusions should be used,
     *            "false"|"off"|"no" when they shouldn't be used.
     */
    public void setDefaultexcludes(boolean useDefaultExcludes) {
        fileset.setDefaultexcludes(useDefaultExcludes);
    }

    /**
     * Turns on/off debugging mode (keeps temp files on disk)
     * 
     * @param debugPCT boolean
     */
    public void setDebugPCT(boolean debugPCT) {
        this.debugPCT = debugPCT;
    }

    /**
     * Do the work
     * 
     * @throws BuildException Something went wrong
     */
    public void execute() throws BuildException {
        ExecTask exec = null;

        checkDlcHome();
        // Library name must be defined
        if ((destFile == null) && (sharedFile == null)) {
            throw new BuildException(Messages.getString("PCTLibrary.1"));
        }

        // There must be at least one fileset
        if ((baseDir == null) && (filesets.size() == 0)) {
            throw new BuildException(Messages.getString("PCTLibrary.2"));
        }

        if ((destFile == null) && (sharedFile != null)) {
            // Only interested in memory-mapped PL file
            destFile = new File(System.getProperty("java.io.tmpdir"), "PCTLib" + tmpLibraryID + ".pl"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            deleteTempFile = true;
            noCompress = true;
        }

        try {
            log(MessageFormat
                    .format(Messages.getString("PCTLibrary.6"), destFile.getAbsolutePath()));

            // Creates new library
            exec = createArchiveTask();
            exec.execute();
            // Parse fileset from task
            if (baseDir != null) {
                exec = addFilesTask(baseDir);
                writeFileList(fileset);
                exec.execute();
            }
            for (FileSet fs : filesets) {
                exec = addFilesTask(fs.getDir(getProject()));
                writeFileList(fs);
                exec.execute();
            }
            // Now adding files containing spaces
            if (spaceFiles.size() > 0) {
                for (File f : spaceFiles.keySet()) {
                    for (String str : spaceFiles.get(f)) {
                        ExecTask task = spaceFileReplace(f, str);
                        task.execute();
                    }
                }
            }

            cleanup();

            // Creates shared library if name defined
            if (sharedFile != null) {
                exec = makeSharedTask();
                exec.execute();
            }

            // Compress library if noCompress set to false
            if (!noCompress) {
                log(MessageFormat.format(Messages.getString("PCTLibrary.7"),
                        destFile.getAbsolutePath()));

                exec = compressTask();
                exec.execute();
            }

            if (deleteTempFile) {
                if (!destFile.delete()) {
                    log(MessageFormat.format(Messages.getString("PCTLibrary.5"),
                            destFile.getAbsolutePath()), Project.MSG_VERBOSE);
                }
            }
        } catch (BuildException be) {
            cleanup();
            throw be;
        }
    }

    private ExecTask createArchiveTask() {
        ExecTask exec = new ExecTask(this);
        exec.setFailonerror(true);

        exec.setExecutable(getExecPath("prolib").toString());
        exec.createArg().setValue(destFile.getAbsolutePath());
        exec.createArg().setValue("-create");

        if (encoding != null) {
            exec.createArg().setValue("-codepage");
            exec.createArg().setValue(encoding);
        }

        exec.createArg().setValue("-nowarn");

        Environment.Variable var = new Environment.Variable();
        var.setKey("DLC");
        var.setValue(getDlcHome().toString());
        exec.addEnv(var);

        return exec;
    }

    private ExecTask addFilesTask(File dir) {
        ExecTask exec = new ExecTask(this);
        exec.setFailonerror(true);
        exec.setDir(dir);

        exec.setExecutable(getExecPath("prolib").toString());
        exec.createArg().setValue(destFile.getAbsolutePath());
        exec.createArg().setValue("-pf");
        exec.createArg().setValue(tmpFile.getAbsolutePath());

        if (cpInternal != null) {
            exec.createArg().setValue("-cpinternal");
            exec.createArg().setValue(cpInternal);
        }
        if (cpStream != null) {
            exec.createArg().setValue("-cpstream");
            exec.createArg().setValue(cpStream);
        }
        if (cpColl != null) {
            exec.createArg().setValue("-cpcoll");
            exec.createArg().setValue(cpColl);
        }
        if (cpCase != null) {
            exec.createArg().setValue("-cpcase");
            exec.createArg().setValue(cpCase);
        }

        Environment.Variable var = new Environment.Variable();
        var.setKey("DLC");
        var.setValue(getDlcHome().toString());
        exec.addEnv(var);

        return exec;
    }

    private ExecTask compressTask() {
        ExecTask exec = new ExecTask(this);
        exec.setFailonerror(true);

        exec.setExecutable(getExecPath("prolib").toString());
        exec.createArg().setValue(destFile.getAbsolutePath());
        exec.createArg().setValue("-compress");

        Environment.Variable var = new Environment.Variable();
        var.setKey("DLC");
        var.setValue(getDlcHome().toString());
        exec.addEnv(var);

        return exec;
    }

    private ExecTask makeSharedTask() {
        ExecTask exec = new ExecTask(this);
        exec.setFailonerror(true);

        exec.setExecutable(getExecPath("prolib").toString());
        exec.createArg().setValue(destFile.getAbsolutePath());
        exec.createArg().setValue("-makeshared");
        exec.createArg().setValue(sharedFile.getAbsolutePath());

        Environment.Variable var = new Environment.Variable();
        var.setKey("DLC");
        var.setValue(getDlcHome().toString());
        exec.addEnv(var);

        return exec;
    }

    /**
     * 
     * @param fs FileSet to be written
     * @throws BuildException
     */
    private void writeFileList(FileSet fs) throws BuildException {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(tmpFile));
            bw.write("-replace ");

            List<String> list = new ArrayList<String>();

            for (String str : fs.getDirectoryScanner(getProject()).getIncludedFiles()) {
                // check not including the pl itself in the pl
                File resourceAsFile = new File(fs.getDir(getProject()), str);
                if (resourceAsFile.equals(destFile)) {
                    bw.close();
                    throw new BuildException(Messages.getString("PCTLibrary.3"));
                }

                // If there are spaces, don't put in the pf file
                if ((str.indexOf(' ') == -1) && (str.length() < 128)) {
                    bw.write(str + " ");
                } else {
                    list.add(str);
                }
            }
            bw.write("-nowarn ");
            bw.close();

            // If there is at least one file with spaces, add the list to spaceFile map
            if (list.size() > 0) {
                spaceFiles.put(fs.getDir(getProject()), list);
            }
        } catch (IOException ioe) {
            throw new BuildException(Messages.getString("PCTLibrary.4"));
        }
    }

    private ExecTask spaceFileReplace(File dir, String fileName) {
        ExecTask exec = new ExecTask(this);
        exec.setDir(dir);
        exec.setFailonerror(true);

        exec.setExecutable(getExecPath("prolib").toString());
        exec.createArg().setValue(destFile.getAbsolutePath());
        exec.createArg().setValue("-replace");
        exec.createArg().setValue(fileName);
        exec.createArg().setValue("-nowarn");

        if (cpInternal != null) {
            exec.createArg().setValue("-cpinternal");
            exec.createArg().setValue(cpInternal);
        }
        if (cpStream != null) {
            exec.createArg().setValue("-cpstream");
            exec.createArg().setValue(cpStream);
        }
        if (cpColl != null) {
            exec.createArg().setValue("-cpcoll");
            exec.createArg().setValue(cpColl);
        }
        if (cpCase != null) {
            exec.createArg().setValue("-cpcase");
            exec.createArg().setValue(cpCase);
        }

        Environment.Variable var = new Environment.Variable();
        var.setKey("DLC");
        var.setValue(getDlcHome().toString());
        exec.addEnv(var);

        return exec;
    }

    /**
     * Delete temporary files if debug not activated
     */
    protected void cleanup() {
        if (!debugPCT) {
            if ((tmpFile != null) && tmpFile.exists() && !tmpFile.delete()) {
                log(MessageFormat.format(Messages.getString("PCTLibrary.5"),
                        tmpFile.getAbsolutePath()), Project.MSG_VERBOSE);
            }
        }
    }

}