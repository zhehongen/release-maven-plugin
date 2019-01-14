package com.m2micro.maven.plugin.release;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Mojo(name = "release", threadSafe = true)
public class ReleaseMojo extends AbstractMojo {


    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    /**
     * 配置文件名
     */
    @Parameter(defaultValue = "application-override.yaml")
    private String configFileName;

    /**
     * 打包后输出目录
     */
   /* @Parameter(defaultValue = "dist")
    private String destDirName;*/

    private final String START_SHELL_NAME = "start.sh";

    public void execute() throws MojoExecutionException, MojoFailureException {

        getLog().info("=========开始发布程序===========");

        String finalName = project.getBuild().getFinalName();
        String productionPath = project.getBuild().getDirectory() + File.separator + "production";
        String targetPath = productionPath + File.separator + finalName;

        try {
            clear(productionPath);
            copyConfig(targetPath);
            copyJar(finalName, targetPath);
            copyShell(targetPath);
            createShell(finalName, targetPath);
            copyReleaseNote(targetPath);
            zip(finalName, productionPath, targetPath);
        } catch (IOException e) {
            getLog().error(e);
        }

        getLog().info("程序文件已经准备完毕");

    }

    private void clear(String productionPath) throws IOException {
        getLog().info("1. 清除发布目录");
        FileUtils.deleteDirectory(new File(productionPath));
    }

    private void copyConfig(String targetPath) throws IOException {
        getLog().info("2. 拷贝配置文件");

        final String outputDirectory = project.getBuild().getOutputDirectory();
        File configFile = new File(outputDirectory + File.separator + configFileName);
        File destDir = new File(targetPath + File.separator + "config");

        if (configFile.exists()) { // 项目中存在配置文件
            FileUtils.copyFileToDirectory(configFile, destDir);
        } else {
            final InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("application-override.yaml");
            if (resourceAsStream != null) {
                configFile = new File(destDir + File.separator + configFileName);
                FileUtils.copyInputStreamToFile(resourceAsStream, configFile);
            }
        }
    }

    private void copyJar(String finalName, String targetPath) throws IOException {
        getLog().info("3. 拷贝运行jar");

        String jarFilePath = project.getBuild().getDirectory() + File.separator + finalName + "." + project.getPackaging();
        File jarFile = new File(jarFilePath);
        File destDir = new File(targetPath + File.separator + "bin");
        FileUtils.copyFileToDirectory(jarFile, destDir);


    }

    private void copyShell(String targetPath) throws IOException {
        getLog().info("4. 拷贝配置运行脚本");

        final InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(START_SHELL_NAME);

        if (resourceAsStream != null) {
            File startSellFile = new File(targetPath + File.separator + "bin" + File.separator + "start.sh");
            boolean rst = startSellFile.setExecutable(true, false);
            FileUtils.copyInputStreamToFile(resourceAsStream, startSellFile);
        }
    }

    private void createShell(String finalName, String targetPath) throws IOException {
        getLog().info("5. 生成最终运行脚本文件");

        File shell = new File(targetPath + File.separator + "run.sh");
        boolean rst = shell.setExecutable(true, false);
        String sb = "#!/usr/bin/env bash" + "\n" +
                "sh bin/start.sh " + finalName + " $1";
        FileUtils.write(shell, sb, encoding);
    }

    private void zip(String finalName, String productionPath, String targetPath) throws IOException {
        getLog().info("开始打包程序...");

        String zipFile = productionPath + File.separator + finalName + ".zip";
        try (OutputStream fos = new FileOutputStream(zipFile);
             OutputStream bos = new BufferedOutputStream(fos);
             final ArchiveOutputStream aos = new ZipArchiveOutputStream(bos)) {
            final Path dirPath = Paths.get(targetPath);
            Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    final ZipArchiveEntry entry = new ZipArchiveEntry(dir.toFile(), dirPath.relativize(dir).toString());
                    aos.putArchiveEntry(entry);
                    aos.closeArchiveEntry();
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    final ZipArchiveEntry entry = new ZipArchiveEntry(file.toFile(), dirPath.relativize(file).toString());
                    aos.putArchiveEntry(entry);
                    IOUtils.copy(new FileInputStream(file.toFile()), aos);
                    aos.closeArchiveEntry();
                    return super.visitFile(file, attrs);
                }
            });

        }
    }

    private void copyReleaseNote(String targetPath) throws IOException {
        String changelogPath = project.getBasedir() + File.separator + "CHANGELOG";
        File targetFile = new File(targetPath);
        File changelogFile = new File(changelogPath);

        FileUtils.copyFileToDirectory(changelogFile, targetFile);

        final InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("README");
        if (resourceAsStream != null) {
            File readmeFile = new File(targetFile + File.separator + "README");
            FileUtils.copyInputStreamToFile(resourceAsStream, readmeFile);
        }
    }

    public String getConfigFileName() {
        return configFileName;
    }

    public void setConfigFileName(String configFileName) {
        this.configFileName = configFileName;
    }

}
