package org.cosmicide.build.dex

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.cosmicide.build.BuildReporter
import org.cosmicide.build.Task
import org.cosmicide.build.util.getSystemClasspath
import org.cosmicide.project.Project
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Task to compile the class files of a project to a Dalvik Executable (Dex) file using D8.
 *
 * @property project The project to compile.
 */
class D8Task(val project: Project) : Task {

    companion object {
        const val MIN_API_LEVEL = 26
        val COMPILATION_MODE = CompilationMode.DEBUG
    }

    /**
     * Compiles the project classes to a Dex file.
     *
     * @param reporter The BuildReporter instance to report any errors to.
     */
    override fun execute(reporter: BuildReporter) {
        try {
            D8.run(
                D8Command.builder()
                    .setMinApiLevel(MIN_API_LEVEL)
                    .setMode(COMPILATION_MODE)
                    .addClasspathFiles(getSystemClasspath().map { it.toPath() })
                    .addProgramFiles(
                        getClassFiles(project.binDir.resolve("classes"))
                    )
                    .setOutput(project.binDir.toPath(), OutputMode.DexIndexed)
                    .build()
            )
        } catch (e: Exception) {
            reporter.reportError("Error compiling project classes: ${e.message}")
        }

        // Compile libraries
        val libDir = project.libDir
        if (libDir.exists() && libDir.isDirectory) {
            val libDexDir = File(project.buildDir, "libs").apply { mkdirs() }
            libDir.listFiles { file -> file.extension == "jar" }.mapNotNull { lib ->
                val outDex = File(libDexDir, lib.nameWithoutExtension + ".dex")
                if (!outDex.exists()) lib.toPath() else null
            }.forEach { jarFile ->
                reporter.reportInfo("Compiling library ${jarFile.name}")
                CoroutineScope(Dispatchers.IO).launch {
                    compileJar(jarFile, libDexDir.toPath(), reporter)
                }
            }
        }
    }

    /**
     * Compiles a jar file to a directory of dex files.
     *
     * @param jarFile The jar file to compile.
     * @param outputDir The directory to output the dex files to.
     * @param reporter The BuildReporter instance to report any errors to.
     */
    fun compileJar(jarFile: Path, outputDir: Path, reporter: BuildReporter) {
        D8.run(
            D8Command.builder()
                .setMinApiLevel(MIN_API_LEVEL)
                .setMode(COMPILATION_MODE)
                .addClasspathFiles(getSystemClasspath().map { it.toPath() })
                .addProgramFiles(jarFile)
                .setOutput(outputDir, OutputMode.DexIndexed)
                .build()
        )
    }

    /**
     * Returns a list of paths to all class files recursively in a directory.
     *
     * @param root The directory to search in.
     * @return A list of paths to all class files in the directory.
     */
    fun getClassFiles(root: File): List<Path> {
        return root.listFiles { file -> file.extension == "class" }
            ?.map { it.toPath() } ?: emptyList()
    }

}